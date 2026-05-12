import Foundation

enum CiboError: LocalizedError {
    case http(Int, String)
    case transport(String)
    case decoding(String)

    var errorDescription: String? {
        switch self {
        case .http(_, let msg):    return msg
        case .transport(let msg):  return msg
        case .decoding(let msg):   return msg
        }
    }
}

actor AnalyzeClient {
    static let shared = AnalyzeClient()

    private let baseURL: URL
    private let session: URLSession
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    init(baseURL: URL? = nil) {
        let configured = (Bundle.main.object(forInfoDictionaryKey: "CIBO_API_BASE_URL") as? String)
            .flatMap(URL.init(string:))
        self.baseURL = baseURL
            ?? configured
            ?? URL(string: "https://cibo-api-op1m.onrender.com")!
        // Render's free tier sleeps after 15 min idle; first request after
        // sleep can take 40-60s to spin up. Generous timeouts absorb that.
        let cfg = URLSessionConfiguration.default
        cfg.timeoutIntervalForRequest = 60
        cfg.timeoutIntervalForResource = 90
        cfg.waitsForConnectivity = true
        self.session = URLSession(configuration: cfg)

        let dec = JSONDecoder()
        dec.keyDecodingStrategy = .convertFromSnakeCase
        self.decoder = dec

        let enc = JSONEncoder()
        enc.keyEncodingStrategy = .convertToSnakeCase
        self.encoder = enc
    }

    // MARK: Health

    /// Fire-and-forget ping to /healthz to wake Render's free-tier dyno.
    /// Called on app launch + on the SignIn screen's `onAppear` so the
    /// dyno is warm by the time the user taps Sign In.
    func wake() async {
        var req = URLRequest(url: baseURL.appendingPathComponent("/healthz"))
        req.timeoutInterval = 60
        _ = try? await session.data(for: req)
    }

    // MARK: Auth

    func register(email: String, password: String, name: String?) async throws -> AuthResponse {
        struct Body: Encodable { let email: String; let password: String; let name: String? }
        return try await post("/auth/register", body: Body(email: email, password: password, name: name))
    }

    func login(email: String, password: String) async throws -> AuthResponse {
        struct Body: Encodable { let email: String; let password: String }
        return try await post("/auth/login", body: Body(email: email, password: password))
    }

    // MARK: Profile

    func getMe(token: String) async throws -> UserPublic {
        try await get("/me", token: token)
    }

    func updateProfile(token: String, update: ProfileUpdate) async throws -> UserPublic {
        try await send("PATCH", path: "/me", body: update, token: token)
    }

    func todaySummary(token: String) async throws -> DailySummary {
        try await get("/me/today", token: token)
    }

    func todayMealLogs(token: String) async throws -> [MealLogPublic] {
        try await get("/me/meal-logs/today", token: token)
    }

    func logMeal(token: String, request: MealLogRequest) async throws -> DailySummary {
        try await post("/meal-logs", body: request, token: token)
    }

    // MARK: Vision

    func analyzePlate(token: String, jpeg: Data) async throws -> PlateAnalyzeResponse {
        try await uploadImage("/analyze-plate", field: "image", filename: "plate.jpg",
                              data: jpeg, token: token)
    }

    func analyzeVision(jpeg: Data, regionHint: String? = nil, token: String? = nil) async throws -> AnalyzeResponse {
        try await uploadImage("/analyze-vision", field: "image", filename: "screen.jpg",
                              data: jpeg, token: token, extraFields: regionHint.map { ["region_hint": $0] } ?? [:])
    }

    // MARK: Plumbing

    private func get<R: Decodable>(_ path: String, token: String? = nil) async throws -> R {
        var req = URLRequest(url: baseURL.appendingPathComponent(path))
        req.httpMethod = "GET"
        attach(token: token, to: &req)
        return try await execute(req)
    }

    private func post<B: Encodable, R: Decodable>(_ path: String, body: B, token: String? = nil) async throws -> R {
        try await send("POST", path: path, body: body, token: token)
    }

    private func send<B: Encodable, R: Decodable>(
        _ method: String, path: String, body: B, token: String?
    ) async throws -> R {
        var req = URLRequest(url: baseURL.appendingPathComponent(path))
        req.httpMethod = method
        req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.httpBody = try encoder.encode(body)
        attach(token: token, to: &req)
        return try await execute(req)
    }

    private func uploadImage<R: Decodable>(
        _ path: String, field: String, filename: String, data: Data,
        token: String?, extraFields: [String: String] = [:]
    ) async throws -> R {
        let boundary = "Boundary-\(UUID().uuidString)"
        var req = URLRequest(url: baseURL.appendingPathComponent(path))
        req.httpMethod = "POST"
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        attach(token: token, to: &req)

        var body = Data()
        for (key, value) in extraFields {
            body.appendString("--\(boundary)\r\n")
            body.appendString("Content-Disposition: form-data; name=\"\(key)\"\r\n\r\n")
            body.appendString("\(value)\r\n")
        }
        body.appendString("--\(boundary)\r\n")
        body.appendString("Content-Disposition: form-data; name=\"\(field)\"; filename=\"\(filename)\"\r\n")
        body.appendString("Content-Type: image/jpeg\r\n\r\n")
        body.append(data)
        body.appendString("\r\n--\(boundary)--\r\n")
        req.httpBody = body
        return try await execute(req)
    }

    private func attach(token: String?, to req: inout URLRequest) {
        if let token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
    }

    private func execute<R: Decodable>(_ req: URLRequest, attempt: Int = 0) async throws -> R {
        do {
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse else {
                throw CiboError.transport("Invalid response")
            }
            guard (200..<300).contains(http.statusCode) else {
                throw CiboError.http(http.statusCode, friendlyError(http.statusCode, data))
            }
            do {
                return try decoder.decode(R.self, from: data)
            } catch {
                throw CiboError.decoding("Could not parse response: \(error.localizedDescription)")
            }
        } catch let urlErr as URLError where attempt == 0 && Self.isRetryable(urlErr) {
            // Stale pooled HTTPS connection — Render killed the socket but
            // URLSession reused it. Try once more with a fresh connection.
            return try await execute(req, attempt: 1)
        } catch let err as CiboError {
            throw err
        } catch {
            throw CiboError.transport(error.localizedDescription)
        }
    }

    private static func isRetryable(_ err: URLError) -> Bool {
        switch err.code {
        case .networkConnectionLost,   // -1005, the classic keep-alive race
             .timedOut,                // -1001, often a cold-start race
             .cannotConnectToHost,     // -1004, dyno mid-restart
             .notConnectedToInternet:  // -1009, simulator hiccup
            return true
        default:
            return false
        }
    }

    private func friendlyError(_ code: Int, _ data: Data) -> String {
        struct Detail: Decodable { let detail: String? }
        if let d = try? JSONDecoder().decode(Detail.self, from: data), let msg = d.detail { return msg }
        return "Request failed (\(code))"
    }
}

private extension Data {
    mutating func appendString(_ str: String) {
        if let d = str.data(using: .utf8) { append(d) }
    }
}
