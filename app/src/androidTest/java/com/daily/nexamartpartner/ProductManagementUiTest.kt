package com.daily.nexamartpartner

import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.di.appContainer
import com.daily.nexamartpartner.features.admin.domain.model.CategoryOption
import com.daily.nexamartpartner.features.admin.domain.model.PagedProducts
import com.daily.nexamartpartner.features.admin.domain.model.ProductAdminAction
import com.daily.nexamartpartner.features.admin.domain.model.ProductAvailability
import com.daily.nexamartpartner.features.admin.domain.model.ProductDetails
import com.daily.nexamartpartner.features.admin.domain.model.ProductDraft
import com.daily.nexamartpartner.features.admin.domain.model.ProductStatus
import com.daily.nexamartpartner.features.admin.domain.model.ProductSummary
import com.daily.nexamartpartner.features.admin.domain.model.ProductsQuery
import com.daily.nexamartpartner.features.admin.domain.repository.ProductManagementRepository
import com.daily.nexamartpartner.features.auth.domain.model.UserRole
import com.daily.nexamartpartner.features.auth.domain.model.UserSession
import java.math.BigDecimal
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductManagementUiTest {

    @After
    fun tearDown() {
        val container = InstrumentationRegistry.getInstrumentation().targetContext.appContainer
        container.productManagementRepositoryOverride = null
    }

    @Test
    fun adminCanOpenProductListFromDashboard() {
        val container = InstrumentationRegistry.getInstrumentation().targetContext.appContainer
        container.productManagementRepositoryOverride = FakeProductRepository()

        ActivityScenario.launch(MainActivity::class.java).onActivity { activity ->
            container.authStateStore.setAuthenticated(session(UserRole.ADMIN))
            val host = activity.supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
            host.navController.navigate(R.id.adminProductsFragment)
        }
        Thread.sleep(700)

        onView(withId(R.id.productsSearchInput)).check(matches(isDisplayed()))
        onView(withId(R.id.addProductButton)).check(matches(isDisplayed()))
        onView(withText("Rice 5kg")).check(matches(isDisplayed()))
    }

    @Test
    fun productListToDetailsAndBackWorks() {
        val container = InstrumentationRegistry.getInstrumentation().targetContext.appContainer
        container.productManagementRepositoryOverride = FakeProductRepository()

        ActivityScenario.launch(MainActivity::class.java).onActivity { activity ->
            container.authStateStore.setAuthenticated(session(UserRole.ADMIN))
            val host = activity.supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
            host.navController.navigate(R.id.adminProductsFragment)
        }
        Thread.sleep(700)

        onView(withText("Rice 5kg")).perform(click())
        onView(withText("Product Details")).check(matches(isDisplayed()))
        onView(withText("DEACTIVATE")).check(matches(isDisplayed()))
        onView(withId(R.id.productDetailsBackButton)).perform(click())
        onView(withId(R.id.productsRecyclerView)).check(matches(isDisplayed()))
    }

    @Test
    fun addProductButtonOpensCreateForm() {
        val container = InstrumentationRegistry.getInstrumentation().targetContext.appContainer
        container.productManagementRepositoryOverride = FakeProductRepository()

        ActivityScenario.launch(MainActivity::class.java).onActivity { activity ->
            container.authStateStore.setAuthenticated(session(UserRole.ADMIN))
            val host = activity.supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
            host.navController.navigate(R.id.adminProductsFragment)
        }
        Thread.sleep(700)

        onView(withId(R.id.addProductButton)).perform(click())
        onView(withText("Add Product")).check(matches(isDisplayed()))
        onView(withId(R.id.productFormSaveButton)).perform(click())
        onView(withText("Product name is required.")).check(matches(isDisplayed()))
    }

    @Test
    fun deliveryPartnerCannotOpenAdminProductManagement() {
        val container = InstrumentationRegistry.getInstrumentation().targetContext.appContainer
        ActivityScenario.launch(MainActivity::class.java).onActivity { activity ->
            container.authStateStore.setAuthenticated(session(UserRole.DELIVERY_PARTNER))
            val host = activity.supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
            host.navController.navigate(R.id.adminProductsFragment)
        }
        onView(withText("NexaMart Delivery")).check(matches(isDisplayed()))
    }

    private fun session(role: UserRole) = UserSession("access", "refresh", 1, "Test", "9999999999", role)

    private class FakeProductRepository : ProductManagementRepository {
        override suspend fun getProducts(query: ProductsQuery): AppResult<PagedProducts> = AppResult.Success(
            PagedProducts(listOf(summary()), 0, 20, 1, 1, false)
        )

        override suspend fun getProductDetails(productId: String): AppResult<ProductDetails> = AppResult.Success(
            ProductDetails(
                productId = productId,
                name = "Rice 5kg",
                description = "Premium rice",
                categoryId = "C1",
                categoryName = "Groceries",
                price = BigDecimal("450.00"),
                discountedPrice = BigDecimal("400.00"),
                discountPercent = BigDecimal("11.11"),
                currencyCode = "INR",
                stock = 10,
                sku = "SKU-1",
                unit = "bag",
                status = ProductStatus.ACTIVE,
                availability = ProductAvailability.IN_STOCK,
                imageUrl = null,
                createdAt = "2026-09-01T10:00:00Z",
                updatedAt = "2026-09-08T10:00:00Z",
                allowedActions = listOf(ProductAdminAction.DEACTIVATE, ProductAdminAction.EDIT)
            )
        )

        override suspend fun getCategoryOptions(): AppResult<List<CategoryOption>> =
            AppResult.Success(listOf(CategoryOption("C1", "Groceries")))

        override suspend fun createProduct(draft: ProductDraft): AppResult<ProductDetails> = getProductDetails("P1001")

        override suspend fun updateProduct(productId: String, draft: ProductDraft): AppResult<ProductDetails> =
            getProductDetails(productId)

        override suspend fun uploadProductImage(
            productId: String,
            image: com.daily.nexamartpartner.features.admin.domain.model.ProductImageUpload
        ): AppResult<ProductDetails> = getProductDetails(productId)

        override suspend fun performProductAction(productId: String, action: ProductAdminAction): AppResult<Unit> =
            AppResult.Success(Unit)

        private fun summary() = ProductSummary(
            productId = "P1001",
            name = "Rice 5kg",
            categoryId = "C1",
            categoryName = "Groceries",
            price = BigDecimal("450.00"),
            discountedPrice = BigDecimal("400.00"),
            currencyCode = "INR",
            stock = 10,
            unit = "bag",
            status = ProductStatus.ACTIVE,
            availability = ProductAvailability.IN_STOCK,
            imageUrl = null
        )
    }
}
