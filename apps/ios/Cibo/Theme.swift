import SwiftUI

enum CiboColor {
    static let background           = Color(hex: 0x08132A)
    static let surfaceContainerLow  = Color(hex: 0x101B33)
    static let surfaceContainer     = Color(hex: 0x151F37)
    static let surfaceContainerHigh = Color(hex: 0x1F2942)
    static let surfaceContainerHighest = Color(hex: 0x2A344D)
    static let surfaceVariant       = Color(hex: 0x2A344D)

    static let onSurface            = Color(hex: 0xD9E2FF)
    static let onSurfaceVariant     = Color(hex: 0xBACAC4)
    static let outline              = Color(hex: 0x85948F)
    static let outlineVariant       = Color(hex: 0x3B4A45)

    static let primary              = Color(hex: 0x4FF1D1)
    static let primaryContainer     = Color(hex: 0x1ED4B6)
    static let onPrimary            = Color(hex: 0x00382E)
    static let primaryFixedDim      = Color(hex: 0x33DEBF)

    static let secondary            = Color(hex: 0xB9C7E4)
    static let secondaryContainer   = Color(hex: 0x3C4962)

    static let warning              = Color(hex: 0xFFC773)
    static let error                = Color(hex: 0xFFB4AB)
    static let success              = primary
}

enum CiboSpacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 16
    static let lg: CGFloat = 24
    static let xl: CGFloat = 32
    static let containerMargin: CGFloat = 20
    static let gutter: CGFloat = 12
}

enum CiboRadius {
    static let sm: CGFloat = 4
    static let base: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
    static let full: CGFloat = 9999
}

enum CiboFont {
    // Variable fonts — use family name and apply weight via SwiftUI's
    // `.weight(...)` axis. SwiftUI silently falls back to system fonts if
    // the TTF isn't bundled in Resources/Fonts/.
    private static let displayFamily = "Fraunces"
    private static let bodyFamily    = "Plus Jakarta Sans"

    static func display(_ size: CGFloat, weight: Font.Weight = .semibold) -> Font {
        Font.custom(displayFamily, size: size).weight(weight)
    }
    static func body(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        Font.custom(bodyFamily, size: size).weight(weight)
    }
    static var displayLg: Font { display(40, weight: .semibold) }
    static var displayMd: Font { display(32, weight: .medium) }
    static var h1:   Font { body(24, weight: .bold) }
    static var h2:   Font { body(20, weight: .semibold) }
    static var bodyLg: Font { body(18, weight: .regular) }
    static var bodyMd: Font { body(16, weight: .regular) }
    static var labelSm: Font { body(12, weight: .semibold) }
}

extension Color {
    init(hex: UInt32, opacity: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255
        let g = Double((hex >>  8) & 0xFF) / 255
        let b = Double((hex >>  0) & 0xFF) / 255
        self.init(.sRGB, red: r, green: g, blue: b, opacity: opacity)
    }
}

extension View {
    func screenPadding() -> some View {
        padding(.horizontal, CiboSpacing.containerMargin)
    }
}
