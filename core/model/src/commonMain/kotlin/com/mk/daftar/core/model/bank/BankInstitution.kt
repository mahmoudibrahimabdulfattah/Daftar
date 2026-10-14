package com.mk.daftar.core.model.bank

import com.mk.daftar.core.model.InstitutionId

private val COUNTRY_CODE = Regex("[A-Z]{2}")
private val HEX_COLOR = Regex("#[0-9A-F]{6}")

/** A country that the bundled directory groups its institutions under. Public catalog metadata only. */
data class BankCountry(val code: String, val nameAr: String, val nameEn: String) {
    init {
        require(COUNTRY_CODE.matches(code)) { "country code must be ISO alpha-2 in uppercase" }
        require(nameAr.isNotBlank() && nameEn.isNotBlank()) { "country names must not be blank" }
    }
}

/**
 * One catalog institution from the bundled directory. The monogram and both colours are always present, even when an
 * official asset exists. [officialAsset] and [manifestRef] are null until an artwork is bundled for the institution.
 * [officialBacking] is `white` for a mark drawn on a white tile, `brand` for a light mark drawn on [brandBackground],
 * and `dark` for a bright mark drawn on the near-black [officialBackground] tile. `dark` is the only backing that needs
 * an [officialBackground], and it is never combined with any other backing.
 */
data class BankInstitution(
    val id: InstitutionId,
    val countryCode: String,
    val nameAr: String,
    val nameEn: String,
    val aliasesAr: List<String>,
    val aliasesEn: List<String>,
    val monogram: String,
    val brandBackground: String,
    val monogramForeground: String,
    val officialAsset: String?,
    val manifestRef: String?,
    val officialBacking: String? = null,
    val officialBackground: String? = null,
) {
    init {
        require(COUNTRY_CODE.matches(countryCode)) { "country code must be ISO alpha-2 in uppercase" }
        require(nameAr.isNotBlank() && nameEn.isNotBlank()) { "institution names must not be blank" }
        require(monogram.isNotBlank() && monogram.length <= MAX_MONOGRAM_LENGTH) {
            "monogram must be 1 to 4 characters"
        }
        require(HEX_COLOR.matches(brandBackground)) { "brand background must be #RRGGBB" }
        require(HEX_COLOR.matches(monogramForeground)) { "monogram foreground must be #RRGGBB" }
        officialAsset?.let { require(it.isNotBlank()) { "official asset key must not be blank" } }
        manifestRef?.let { require(it.isNotBlank()) { "manifest reference must not be blank" } }
        require(officialAsset == null || officialBacking in OFFICIAL_BACKINGS) {
            "an official asset needs a white, brand or dark backing"
        }
        officialBackground?.let { require(HEX_COLOR.matches(it)) { "official background must be #RRGGBB" } }
        require((officialBacking == BACKING_DARK) == (officialBackground != null)) {
            "only a dark backing carries an official background"
        }
    }

    companion object {
        const val MAX_MONOGRAM_LENGTH: Int = 4
        const val BACKING_WHITE: String = "white"
        const val BACKING_BRAND: String = "brand"
        const val BACKING_DARK: String = "dark"
        val OFFICIAL_BACKINGS: Set<String> = setOf(BACKING_WHITE, BACKING_BRAND, BACKING_DARK)
    }
}

/** How official artwork is packaged. Chosen once at build time by `-Pdaftar.bankArtwork`, never by the user. */
enum class BankArtworkMode(val gradleValue: String) {
    OFFICIAL("official"),
    MONOGRAM("monogram"),
}
