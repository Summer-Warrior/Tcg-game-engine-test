package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EdoProBaseColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF00262E),
    primaryContainer = CyanContainer,
    onPrimaryContainer = OnCyanContainer,
    secondary = RadiantGold,
    onSecondary = Color(0xFF281900),
    secondaryContainer = GoldContainer,
    onSecondaryContainer = OnGoldContainer,
    tertiary = ArcaneViolet,
    onTertiary = Color(0xFF1F0047),
    tertiaryContainer = VioletContainer,
    onTertiaryContainer = OnVioletContainer,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = CrimsonAlert,
    onError = Color.White
)

private val EdoProCustom21ColorScheme = darkColorScheme(
    primary = RadiantGold,
    onPrimary = Color(0xFF2A1B00),
    primaryContainer = GoldContainer,
    onPrimaryContainer = OnGoldContainer,
    secondary = ElectricCyan,
    onSecondary = Color(0xFF00262E),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = OnCyanContainer,
    tertiary = ArcaneViolet,
    onTertiary = Color(0xFF1F0047),
    tertiaryContainer = VioletContainer,
    onTertiaryContainer = OnVioletContainer,
    background = Color(0xFF100D08),
    onBackground = TextPrimary,
    surface = Color(0xFF1B160E),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF261F13),
    onSurfaceVariant = Color(0xFFD6C5A5),
    outline = Color(0xFF4D3E22),
    error = CrimsonAlert,
    onError = Color.White
)

private val AetherManaColorScheme = darkColorScheme(
    primary = EmeraldReady,
    onPrimary = Color(0xFF002912),
    primaryContainer = Color(0xFF003D1E),
    onPrimaryContainer = Color(0xFFA6FFD0),
    secondary = ElectricCyan,
    onSecondary = Color(0xFF00262E),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = OnCyanContainer,
    tertiary = RadiantGold,
    onTertiary = Color(0xFF281900),
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = OnGoldContainer,
    background = Color(0xFF07120D),
    onBackground = TextPrimary,
    surface = Color(0xFF0E1D16),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF152A20),
    onSurfaceVariant = Color(0xFFA2C4B2),
    outline = Color(0xFF244736),
    error = CrimsonAlert,
    onError = Color.White
)

private val CyberOverdriveColorScheme = darkColorScheme(
    primary = ArcaneViolet,
    onPrimary = Color(0xFF1F0047),
    primaryContainer = VioletContainer,
    onPrimaryContainer = OnVioletContainer,
    secondary = ElectricCyan,
    onSecondary = Color(0xFF00262E),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = OnCyanContainer,
    tertiary = RadiantGold,
    onTertiary = Color(0xFF281900),
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = OnGoldContainer,
    background = Color(0xFF0E091A),
    onBackground = TextPrimary,
    surface = Color(0xFF171029),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF23183D),
    onSurfaceVariant = Color(0xFFBCA9E0),
    outline = Color(0xFF3E2B6B),
    error = CrimsonAlert,
    onError = Color.White
)

private val ChronosRiftColorScheme = darkColorScheme(
    primary = CrimsonAlert,
    onPrimary = Color(0xFF360006),
    primaryContainer = Color(0xFF4D0E16),
    onPrimaryContainer = Color(0xFFFFD9DC),
    secondary = RadiantGold,
    onSecondary = Color(0xFF281900),
    secondaryContainer = GoldContainer,
    onSecondaryContainer = OnGoldContainer,
    tertiary = ElectricCyan,
    onTertiary = Color(0xFF00262E),
    tertiaryContainer = CyanContainer,
    onTertiaryContainer = OnCyanContainer,
    background = Color(0xFF14090C),
    onBackground = TextPrimary,
    surface = Color(0xFF211015),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF30171F),
    onSurfaceVariant = Color(0xFFD9ABB3),
    outline = Color(0xFF592B38),
    error = CrimsonAlert,
    onError = Color.White
)

private val ForgeMtgBaseColorScheme = darkColorScheme(
    primary = Color(0xFF29B6F6),
    onPrimary = Color(0xFF002538),
    primaryContainer = Color(0xFF0A3954),
    onPrimaryContainer = Color(0xFFB3E5FC),
    secondary = RadiantGold,
    onSecondary = Color(0xFF281900),
    secondaryContainer = GoldContainer,
    onSecondaryContainer = OnGoldContainer,
    tertiary = EmeraldReady,
    onTertiary = Color(0xFF002912),
    tertiaryContainer = Color(0xFF003D1E),
    onTertiaryContainer = Color(0xFFA6FFD0),
    background = Color(0xFF071018),
    onBackground = TextPrimary,
    surface = Color(0xFF0D1B28),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF14283B),
    onSurfaceVariant = Color(0xFFA9C7DF),
    outline = Color(0xFF254766),
    error = CrimsonAlert,
    onError = Color.White
)

private val ForgeCustomCloneColorScheme = darkColorScheme(
    primary = Color(0xFF66BB6A),
    onPrimary = Color(0xFF002908),
    primaryContainer = Color(0xFF133F17),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = RadiantGold,
    onSecondary = Color(0xFF281900),
    secondaryContainer = GoldContainer,
    onSecondaryContainer = OnGoldContainer,
    tertiary = Color(0xFF29B6F6),
    onTertiary = Color(0xFF002538),
    tertiaryContainer = Color(0xFF0A3954),
    onTertiaryContainer = Color(0xFFB3E5FC),
    background = Color(0xFF09120B),
    onBackground = TextPrimary,
    surface = Color(0xFF102013),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF18301D),
    onSurfaceVariant = Color(0xFFB4D3B8),
    outline = Color(0xFF2D5434),
    error = CrimsonAlert,
    onError = Color.White
)

private val HybridForgeEdoProMixColorScheme = darkColorScheme(
    primary = Color(0xFFFF4081),
    onPrimary = Color(0xFF3B0016),
    primaryContainer = Color(0xFF590D29),
    onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = ElectricCyan,
    onSecondary = Color(0xFF00262E),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = OnCyanContainer,
    tertiary = RadiantGold,
    onTertiary = Color(0xFF281900),
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = OnGoldContainer,
    background = Color(0xFF140914),
    onBackground = TextPrimary,
    surface = Color(0xFF201022),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF2E1733),
    onSurfaceVariant = Color(0xFFE0B4E6),
    outline = Color(0xFF5C2B66),
    error = CrimsonAlert,
    onError = Color.White
)

fun colorSchemeForBaseVariant(baseVariantId: String): ColorScheme {
    return when (baseVariantId) {
        "EDOPRO_CUSTOM_FORK" -> EdoProCustom21ColorScheme
        "FORGE_MTG_BASE" -> ForgeMtgBaseColorScheme
        "FORGE_CUSTOM_CLONE" -> ForgeCustomCloneColorScheme
        "HYBRID_FORGE_EDOPRO_MIX" -> HybridForgeEdoProMixColorScheme
        "AETHER_MANA_V1" -> AetherManaColorScheme
        "CYBER_OVERDRIVE_V2" -> CyberOverdriveColorScheme
        "CHRONOS_RIFT_V1" -> ChronosRiftColorScheme
        else -> EdoProBaseColorScheme
    }
}

@Composable
fun MyApplicationTheme(
    baseVariantId: String = "EDOPRO_BASE_MR5",
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = colorSchemeForBaseVariant(baseVariantId),
        typography = Typography,
        content = content
    )
}
