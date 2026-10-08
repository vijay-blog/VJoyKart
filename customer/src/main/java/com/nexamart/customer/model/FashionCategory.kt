package com.nexamart.customer.model

/** Local fashion navigation from home_screen.dart (`_fashionCategories`). */
data class FashionCategory(val name: String, val asset: String, val keywords: List<String>)

object FashionCatalog {
    val categories = listOf(
        FashionCategory("Men", "assets/images/products/tshirt.png", listOf("men", "mens", "man")),
        FashionCategory("Women", "assets/images/products/women_dress.png", listOf("women", "womens", "woman")),
        FashionCategory("Boys", "assets/images/products/boys_clothing.png", listOf("boys", "boy")),
        FashionCategory("Girls", "assets/images/products/girls_clothing.png", listOf("girls", "girl")),
        FashionCategory("Kids Wear", "assets/images/products/boys_clothing.png", listOf("kids", "kid", "children", "boys", "girls")),
        FashionCategory("T-Shirts", "assets/images/products/tshirt.png", listOf("t-shirt", "tshirt", "tee")),
        FashionCategory("Shirts", "assets/images/products/tshirt.png", listOf("shirt")),
        FashionCategory("Jeans", "assets/images/products/tshirt.png", listOf("jeans", "denim")),
        FashionCategory("Dresses", "assets/images/products/dress.png", listOf("dress")),
        FashionCategory("Ethnic Wear", "assets/images/products/women_dress.png", listOf("ethnic", "saree", "sari", "kurti", "kurta", "lehenga", "salwar")),
        FashionCategory("Western Wear", "assets/images/products/dress.png", listOf("western", "top", "skirt", "jacket")),
        FashionCategory("Innerwear", "assets/images/products/tshirt.png", listOf("innerwear", "underwear")),
        FashionCategory("Nightwear", "assets/images/products/tshirt.png", listOf("nightwear", "sleepwear")),
        FashionCategory("Sportswear", "assets/images/products/tshirt.png", listOf("sportswear", "track pant", "sports")),
        FashionCategory("Winterwear", "assets/images/products/tshirt.png", listOf("winterwear", "hoodie", "sweater", "jacket", "coat")),
        FashionCategory("Trousers", "assets/images/products/tshirt.png", listOf("trouser", "pants")),
        FashionCategory("Shorts", "assets/images/products/tshirt.png", listOf("shorts")),
        FashionCategory("Sarees", "assets/images/products/women_dress.png", listOf("saree", "sari")),
        FashionCategory("Kurtis", "assets/images/products/women_dress.png", listOf("kurti", "kurta")),
        FashionCategory("Lehengas", "assets/images/products/women_dress.png", listOf("lehenga")),
        FashionCategory("Leggings", "assets/images/products/tshirt.png", listOf("leggings")),
        FashionCategory("Palazzo", "assets/images/products/women_dress.png", listOf("palazzo")),
        FashionCategory("Party Wear", "assets/images/products/dress.png", listOf("party wear", "party")),
        FashionCategory("Casual Wear", "assets/images/products/tshirt.png", listOf("casual wear", "casual")),
        FashionCategory("Formal Wear", "assets/images/products/tshirt.png", listOf("formal wear", "formal", "blazer")),
        FashionCategory("Baby Wear", "assets/images/products/girls_clothing.png", listOf("baby", "newborn")),
    )

    val MEN = listOf("men", "mens", "man")
    val WOMEN = listOf("women", "womens", "woman")
    val KIDS = listOf("kids", "boys", "girls", "kid", "boy", "girl")

    private val NON_ALNUM = Regex("[^a-z0-9]+")

    private fun normalize(text: String) = text.lowercase().replace(NON_ALNUM, " ").trim()

    private fun matches(raw: String, keywords: List<String>): Boolean {
        val padded = " ${normalize(raw)} "
        return keywords.any { keyword ->
            val value = normalize(keyword)
            value.isNotEmpty() && padded.contains(" $value ")
        }
    }

    /** Home section matching (`_matchesAny` in home_screen.dart). */
    fun matchesHomeSection(product: Product, keywords: List<String>): Boolean =
        matches(listOf(product.name, product.categoryName, product.description, product.brand).joinToString(" "), keywords)

    /** Category screen matching (FashionCategoryScreen._matches, includes attribute values). */
    fun matchesCategory(product: Product, keywords: List<String>): Boolean = matches(
        listOf(
            product.name,
            product.categoryName,
            product.description,
            product.brand,
            product.attributes.values.joinToString(" "),
        ).joinToString(" "),
        keywords,
    )
}
