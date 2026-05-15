import SwiftUI

/// Avatar with three rendering modes:
/// - User has uploaded a photo  → AsyncImage fetched from the resolved URL
/// - User has no photo          → initials in a teal-tinted circle
/// - User is nil (signed out)   → "+" placeholder
struct CiboAvatar: View {
    var user: UserPublic?
    var size: CGFloat = 38
    var stroked: Bool = false

    var body: some View {
        let url = AnalyzeClient.shared.resolvePicture(user?.picture)
        ZStack {
            Circle().fill(CiboColor.surfaceContainerHigh)
            if let url {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    default:
                        InitialsView(user: user)
                    }
                }
                .clipShape(Circle())
            } else {
                InitialsView(user: user)
            }
        }
        .frame(width: size, height: size)
        .overlay {
            if stroked {
                Circle().strokeBorder(CiboColor.primary, lineWidth: 2)
            }
        }
    }

    private struct InitialsView: View {
        var user: UserPublic?
        var body: some View {
            Text(initials)
                .font(CiboFont.body(13, weight: .bold))
                .foregroundStyle(user == nil ? CiboColor.onSurfaceVariant : CiboColor.primary)
        }
        private var initials: String {
            guard let user else { return "+" }
            let name = user.name ?? user.email
            let parts = name.split(separator: " ")
            if parts.count >= 2, let f = parts.first?.first, let s = parts[1].first {
                return "\(f)\(s)".uppercased()
            }
            return String(name.prefix(2)).uppercased()
        }
    }
}
