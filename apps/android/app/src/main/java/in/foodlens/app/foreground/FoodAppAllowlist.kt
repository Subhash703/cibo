package `in`.foodlens.app.foreground

/**
 * Hard-coded list of food / grocery delivery apps that should trigger the
 * floating bubble. Anything else (Instagram, banking, our own app) is treated
 * as "not a food moment" and the bubble stays hidden.
 *
 * This list is the single source of truth for the deck's "Food Only, Always"
 * promise. We deliberately keep it short and India-first for v0.1; the
 * intent is to extend this list (or sideload it from the backend) as we add
 * more partners.
 */
object FoodAppAllowlist {

    val PACKAGES: Set<String> = setOf(
        // India — food delivery
        "in.swiggy.android",
        "com.application.zomato",
        // India — quick-commerce / grocery
        "com.bigbasket.mobileapp",
        "com.grofers.customerapp", // Blinkit
        "in.dunzo.user",
        // QSR
        "com.dominos",
        "com.dominos.app.android",
        "com.mcdonalds.mobileapp",
        "com.yum.kfc",
        "com.starbucks.mobilecard",
        // Global (useful when testing on non-IN devices)
        "com.ubercab.eats",
        "com.dd.doordash",
    )

    fun matches(packageName: String?): Boolean =
        packageName != null && packageName in PACKAGES
}
