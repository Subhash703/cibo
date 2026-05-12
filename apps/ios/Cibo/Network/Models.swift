import Foundation

// Mirrors apps/backend Pydantic models. Field names use snake_case on the
// wire — JSONDecoder is configured with .convertFromSnakeCase.

struct Macro: Codable, Hashable {
    var kcal: Int
    var proteinG: Int
    var fatG: Int
    var carbsG: Int
}

struct Suggestion: Codable, Hashable {
    var text: String
    var kcalDelta: Int
}

struct MatchedItem: Codable, Hashable {
    var name: String
    var qty: Int
    var kcal: Int
}

struct DailySummary: Codable, Hashable {
    var dailyKcalTarget: Int
    var consumedKcal: Int
    var remainingKcal: Int
    var consumedProteinG: Int = 0
    var consumedFatG: Int = 0
    var consumedCarbsG: Int = 0
    var logCount: Int = 0
}

struct AnalyzeResponse: Codable {
    var healthScore: Int
    var healthLabel: String
    var macros: Macro
    var percentDailyKcal: Int
    var suggestions: [Suggestion]
    var items: [MatchedItem] = []
    var unmatched: [String] = []
    var dailySummary: DailySummary?
}

struct CoachVerdict: Codable, Hashable {
    var signal: String   // "green" | "yellow" | "red"
    var oneLiner: String
    var reason: String = ""
}

struct PlateAnalyzeResponse: Codable {
    var dishName: String
    var dishDescription: String
    var macros: Macro
    var healthScore: Int
    var healthLabel: String
    var verdict: CoachVerdict
    var dailySummary: DailySummary?
}

struct UserPublic: Codable, Hashable {
    var email: String
    var name: String?
    var picture: String?
    var dailyKcalTarget: Int = 2000
    var birthYear: Int?
    var sex: String?
    var weightKg: Double?
    var heightCm: Double?
    var activityLevel: String?
    var suggestedKcalTarget: Int?
}

struct AuthResponse: Codable {
    var token: String
    var user: UserPublic
}

struct ProfileUpdate: Codable {
    var dailyKcalTarget: Int?
    var birthYear: Int?
    var sex: String?
    var weightKg: Double?
    var heightCm: Double?
    var activityLevel: String?
}

struct MealLogRequest: Codable {
    var macros: Macro
    var items: [MatchedItem]
    var healthScore: Int
}

struct MealLogPublic: Codable, Identifiable, Hashable {
    var id: Int
    var loggedAt: String
    var kcal: Int
    var proteinG: Int
    var fatG: Int
    var carbsG: Int
    var healthScore: Int
    var items: [MatchedItem] = []
}
