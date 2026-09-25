package com.daily.nexamartpartner.di

import android.content.Context
import com.daily.nexamartpartner.core.network.ApiCallExecutor
import com.daily.nexamartpartner.core.network.ApiClientFactory
import com.daily.nexamartpartner.core.network.AuthHeaderInterceptor
import com.daily.nexamartpartner.core.network.NetworkConnectivityMonitor
import com.daily.nexamartpartner.core.security.EncryptedSessionStorage
import com.daily.nexamartpartner.features.admin.data.contract.AdminDashboardContract
import com.daily.nexamartpartner.features.admin.data.contract.AdminOrdersContract
import com.daily.nexamartpartner.features.admin.data.contract.PendingBackendAdminDashboardContract
import com.daily.nexamartpartner.features.admin.data.contract.PendingBackendAdminOrdersContract
import com.daily.nexamartpartner.features.admin.data.contract.DeliveryPartnerContract
import com.daily.nexamartpartner.features.admin.data.contract.PendingBackendDeliveryPartnerContract
import com.daily.nexamartpartner.features.admin.data.contract.BackendProductManagementContract
import com.daily.nexamartpartner.features.admin.data.contract.ProductManagementContract
import com.daily.nexamartpartner.features.admin.category.data.contract.CategoryManagementContract
import com.daily.nexamartpartner.features.admin.category.data.contract.BackendCategoryManagementContract
import com.daily.nexamartpartner.features.admin.category.data.repository.CategoryManagementRepositoryImpl
import com.daily.nexamartpartner.features.admin.category.data.source.CategoryManagementApi
import com.daily.nexamartpartner.features.admin.category.data.source.CategoryManagementRemoteDataSource
import com.daily.nexamartpartner.features.admin.category.data.source.CategoryManagementRemoteDataSourceImpl
import com.daily.nexamartpartner.features.admin.category.domain.repository.CategoryManagementRepository
import com.daily.nexamartpartner.features.admin.category.domain.usecase.*
import com.daily.nexamartpartner.features.admin.customer.data.contract.CustomerManagementContract
import com.daily.nexamartpartner.features.admin.customer.data.contract.PendingBackendCustomerManagementContract
import com.daily.nexamartpartner.features.admin.customer.data.repository.CustomerManagementRepositoryImpl
import com.daily.nexamartpartner.features.admin.customer.data.source.CustomerManagementApi
import com.daily.nexamartpartner.features.admin.customer.data.source.CustomerManagementRemoteDataSource
import com.daily.nexamartpartner.features.admin.customer.data.source.CustomerManagementRemoteDataSourceImpl
import com.daily.nexamartpartner.features.admin.customer.domain.repository.CustomerManagementRepository
import com.daily.nexamartpartner.features.admin.customer.domain.usecase.GetCustomerDetailsUseCase
import com.daily.nexamartpartner.features.admin.customer.domain.usecase.GetCustomersUseCase
import com.daily.nexamartpartner.features.admin.customer.domain.usecase.PerformCustomerAdminActionUseCase
import com.daily.nexamartpartner.features.admin.data.repository.AdminDashboardRepositoryImpl
import com.daily.nexamartpartner.features.admin.data.repository.AdminOrdersRepositoryImpl
import com.daily.nexamartpartner.features.admin.data.repository.DeliveryPartnerRepositoryImpl
import com.daily.nexamartpartner.features.admin.data.repository.ProductManagementRepositoryImpl
import com.daily.nexamartpartner.features.admin.data.source.AdminDashboardApi
import com.daily.nexamartpartner.features.admin.data.source.AdminDashboardRemoteDataSource
import com.daily.nexamartpartner.features.admin.data.source.AdminDashboardRemoteDataSourceImpl
import com.daily.nexamartpartner.features.admin.data.source.AdminOrdersApi
import com.daily.nexamartpartner.features.admin.data.source.AdminOrdersRemoteDataSource
import com.daily.nexamartpartner.features.admin.data.source.AdminOrdersRemoteDataSourceImpl
import com.daily.nexamartpartner.features.admin.data.source.DeliveryPartnerApi
import com.daily.nexamartpartner.features.admin.data.source.DeliveryPartnerRemoteDataSource
import com.daily.nexamartpartner.features.admin.data.source.DeliveryPartnerRemoteDataSourceImpl
import com.daily.nexamartpartner.features.admin.data.source.ProductManagementApi
import com.daily.nexamartpartner.features.admin.data.source.ProductManagementRemoteDataSource
import com.daily.nexamartpartner.features.admin.data.source.ProductManagementRemoteDataSourceImpl
import com.daily.nexamartpartner.features.admin.domain.repository.AdminDashboardRepository
import com.daily.nexamartpartner.features.admin.domain.repository.AdminOrdersRepository
import com.daily.nexamartpartner.features.admin.domain.repository.DeliveryPartnerRepository
import com.daily.nexamartpartner.features.admin.domain.repository.ProductManagementRepository
import com.daily.nexamartpartner.features.admin.domain.usecase.CancelAdminOrderUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.CreateProductUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.GetAdminDashboardUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.GetAdminOrderDetailsUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.GetAdminOrdersUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.UpdateAdminOrderStatusUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.GetDeliveryPartnerDetailsUseCase
import com.daily.nexamartpartner.features.delivery.data.contract.DeliveryDashboardContract
import com.daily.nexamartpartner.features.delivery.data.contract.PendingBackendDeliveryDashboardContract
import com.daily.nexamartpartner.features.delivery.data.contract.DeliveryOrderWorkflowContract
import com.daily.nexamartpartner.features.delivery.data.contract.PendingBackendDeliveryOrderWorkflowContract
import com.daily.nexamartpartner.features.delivery.data.repository.DeliveryOrderWorkflowRepositoryImpl
import com.daily.nexamartpartner.features.delivery.data.source.DeliveryOrderWorkflowApi
import com.daily.nexamartpartner.features.delivery.data.source.DeliveryOrderWorkflowDataSource
import com.daily.nexamartpartner.features.delivery.data.source.DeliveryOrderWorkflowDataSourceImpl
import com.daily.nexamartpartner.features.delivery.domain.repository.DeliveryOrderWorkflowRepository
import com.daily.nexamartpartner.features.delivery.domain.usecase.GetAssignedDeliveryOrdersUseCase
import com.daily.nexamartpartner.features.delivery.domain.usecase.GetDeliveryOrderDetailsUseCase
import com.daily.nexamartpartner.features.delivery.domain.usecase.PerformDeliveryOrderActionUseCase
import com.daily.nexamartpartner.features.delivery.data.repository.DeliveryDashboardRepositoryImpl
import com.daily.nexamartpartner.features.delivery.data.source.DeliveryDashboardApi
import com.daily.nexamartpartner.features.delivery.data.source.DeliveryDashboardDataSource
import com.daily.nexamartpartner.features.delivery.data.source.DeliveryDashboardDataSourceImpl
import com.daily.nexamartpartner.features.delivery.domain.repository.DeliveryDashboardRepository
import com.daily.nexamartpartner.features.delivery.domain.usecase.GetDeliveryDashboardUseCase
import com.daily.nexamartpartner.features.delivery.domain.usecase.GetDeliveryHistoryUseCase
import com.daily.nexamartpartner.features.delivery.earnings.data.contract.DeliveryEarningsContract
import com.daily.nexamartpartner.features.delivery.earnings.data.contract.PendingBackendDeliveryEarningsContract
import com.daily.nexamartpartner.features.delivery.earnings.data.repository.DeliveryEarningsRepositoryImpl
import com.daily.nexamartpartner.features.delivery.earnings.data.source.DeliveryEarningsApi
import com.daily.nexamartpartner.features.delivery.earnings.data.source.DeliveryEarningsDataSource
import com.daily.nexamartpartner.features.delivery.earnings.data.source.DeliveryEarningsDataSourceImpl
import com.daily.nexamartpartner.features.delivery.earnings.domain.repository.DeliveryEarningsRepository
import com.daily.nexamartpartner.features.delivery.earnings.domain.usecase.GetDeliveryEarningsSummaryUseCase
import com.daily.nexamartpartner.features.delivery.earnings.domain.usecase.GetDeliveryEarningsHistoryUseCase
import com.daily.nexamartpartner.features.delivery.notifications.data.contract.DeliveryNotificationsContract
import com.daily.nexamartpartner.features.delivery.profile.data.contract.DeliveryPartnerProfileContract
import com.daily.nexamartpartner.features.delivery.profile.data.contract.PendingBackendDeliveryPartnerProfileContract
import com.daily.nexamartpartner.features.delivery.profile.data.repository.DeliveryPartnerProfileRepositoryImpl
import com.daily.nexamartpartner.features.delivery.profile.data.source.DeliveryPartnerProfileApi
import com.daily.nexamartpartner.features.delivery.profile.data.source.DeliveryPartnerProfileDataSource
import com.daily.nexamartpartner.features.delivery.profile.data.source.DeliveryPartnerProfileDataSourceImpl
import com.daily.nexamartpartner.features.delivery.profile.domain.repository.DeliveryPartnerProfileRepository
import com.daily.nexamartpartner.features.delivery.profile.domain.usecase.GetDeliveryPartnerProfileUseCase
import com.daily.nexamartpartner.features.delivery.profile.domain.usecase.UpdateDeliveryPartnerProfileUseCase
import com.daily.nexamartpartner.features.delivery.availability.data.contract.DeliveryAvailabilityContract
import com.daily.nexamartpartner.features.delivery.availability.data.contract.PendingBackendDeliveryAvailabilityContract
import com.daily.nexamartpartner.features.delivery.availability.data.repository.DeliveryAvailabilityRepositoryImpl
import com.daily.nexamartpartner.features.delivery.availability.data.source.DeliveryAvailabilityApi
import com.daily.nexamartpartner.features.delivery.availability.data.source.DeliveryAvailabilityDataSource
import com.daily.nexamartpartner.features.delivery.availability.data.source.DeliveryAvailabilityDataSourceImpl
import com.daily.nexamartpartner.features.delivery.availability.domain.repository.DeliveryAvailabilityRepository
import com.daily.nexamartpartner.features.delivery.availability.domain.usecase.GetDeliveryAvailabilityUseCase
import com.daily.nexamartpartner.features.delivery.availability.domain.usecase.UpdateDeliveryAvailabilityUseCase
import com.daily.nexamartpartner.features.delivery.notifications.data.contract.PendingBackendDeliveryNotificationsContract
import com.daily.nexamartpartner.features.delivery.notifications.data.repository.DeliveryNotificationsRepositoryImpl
import com.daily.nexamartpartner.features.delivery.notifications.data.source.DeliveryNotificationsApi
import com.daily.nexamartpartner.features.delivery.notifications.data.source.DeliveryNotificationsDataSource
import com.daily.nexamartpartner.features.delivery.notifications.domain.repository.DeliveryNotificationsRepository
import com.daily.nexamartpartner.features.delivery.notifications.domain.usecase.GetDeliveryNotificationsUseCase
import com.daily.nexamartpartner.features.delivery.notifications.domain.usecase.MarkAllDeliveryNotificationsReadUseCase
import com.daily.nexamartpartner.features.delivery.notifications.domain.usecase.MarkDeliveryNotificationReadUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.GetDeliveryPartnersUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.GetProductCategoryOptionsUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.GetProductDetailsUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.GetProductsUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.PerformProductAdminActionUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.UpdateDeliveryPartnerUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.UpdateProductUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.UploadProductImageUseCase
import com.daily.nexamartpartner.features.auth.data.contract.AuthRequestContract
import com.daily.nexamartpartner.features.auth.data.contract.ConfigurableAuthRequestContract
import com.daily.nexamartpartner.features.auth.data.repository.AuthRepositoryImpl
import com.daily.nexamartpartner.features.auth.data.source.AuthApi
import com.daily.nexamartpartner.features.auth.data.source.AuthRemoteDataSource
import com.daily.nexamartpartner.features.auth.data.source.AuthRemoteDataSourceImpl
import com.daily.nexamartpartner.features.auth.data.contract.RegistrationRequestContract
import com.daily.nexamartpartner.features.auth.domain.repository.AuthRepository
import com.daily.nexamartpartner.features.auth.domain.session.SessionManager
import com.daily.nexamartpartner.features.auth.domain.usecase.LoginUseCase
import com.daily.nexamartpartner.features.auth.domain.usecase.RegisterUseCase
import com.daily.nexamartpartner.features.auth.domain.usecase.LogoutUseCase
import com.daily.nexamartpartner.features.auth.domain.usecase.RestoreSessionUseCase
import com.daily.nexamartpartner.features.auth.presentation.state.AuthStateStore
import retrofit2.Retrofit

class AppContainer(context: Context) {
    val networkConnectivityMonitor = NetworkConnectivityMonitor(context.applicationContext)

    private val sessionStorage = EncryptedSessionStorage(context.applicationContext)
    val sessionManager = SessionManager(sessionStorage)
    val authStateStore = AuthStateStore()

    private val authHeaderInterceptor = AuthHeaderInterceptor {
        sessionManager.currentSession.value?.accessToken
    }

    private val retrofit: Retrofit = ApiClientFactory.create(authHeaderInterceptor, networkConnectivityMonitor)
    private val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    private val adminDashboardApi: AdminDashboardApi = retrofit.create(AdminDashboardApi::class.java)
    private val adminOrdersApi: AdminOrdersApi = retrofit.create(AdminOrdersApi::class.java)
    private val deliveryPartnerApi: DeliveryPartnerApi = retrofit.create(DeliveryPartnerApi::class.java)
    private val productManagementApi: ProductManagementApi = retrofit.create(ProductManagementApi::class.java)
    private val categoryManagementApi: CategoryManagementApi = retrofit.create(CategoryManagementApi::class.java)
    private val customerManagementApi: CustomerManagementApi = retrofit.create(CustomerManagementApi::class.java)
    private val deliveryDashboardApi: DeliveryDashboardApi = retrofit.create(DeliveryDashboardApi::class.java)
    private val deliveryOrderWorkflowApi: DeliveryOrderWorkflowApi = retrofit.create(DeliveryOrderWorkflowApi::class.java)
    private val deliveryEarningsApi: DeliveryEarningsApi = retrofit.create(DeliveryEarningsApi::class.java)
    private val deliveryNotificationsApi: DeliveryNotificationsApi = retrofit.create(DeliveryNotificationsApi::class.java)
    private val deliveryPartnerProfileApi: DeliveryPartnerProfileApi = retrofit.create(DeliveryPartnerProfileApi::class.java)
    private val deliveryAvailabilityApi: DeliveryAvailabilityApi = retrofit.create(DeliveryAvailabilityApi::class.java)
    private val authRequestContract: AuthRequestContract = ConfigurableAuthRequestContract()
    private val registrationRequestContract = RegistrationRequestContract()
    private val adminDashboardContract: AdminDashboardContract = PendingBackendAdminDashboardContract()
    private val adminOrdersContract: AdminOrdersContract = PendingBackendAdminOrdersContract()
    private val deliveryPartnerContract: DeliveryPartnerContract = PendingBackendDeliveryPartnerContract()
    private val productManagementContract: ProductManagementContract = BackendProductManagementContract()
    private val categoryManagementContract: CategoryManagementContract = BackendCategoryManagementContract()
    private val customerManagementContract: CustomerManagementContract = PendingBackendCustomerManagementContract()
    private val deliveryDashboardContract: DeliveryDashboardContract = PendingBackendDeliveryDashboardContract()
    private val deliveryOrderWorkflowContract: DeliveryOrderWorkflowContract = PendingBackendDeliveryOrderWorkflowContract()
    private val deliveryEarningsContract: DeliveryEarningsContract = PendingBackendDeliveryEarningsContract()
    private val deliveryNotificationsContract: DeliveryNotificationsContract = PendingBackendDeliveryNotificationsContract()
    private val deliveryPartnerProfileContract: DeliveryPartnerProfileContract = PendingBackendDeliveryPartnerProfileContract()
    private val deliveryAvailabilityContract: DeliveryAvailabilityContract = PendingBackendDeliveryAvailabilityContract()
    private val apiCallExecutor = ApiCallExecutor()

    private val authRemoteDataSource: AuthRemoteDataSource = AuthRemoteDataSourceImpl(
        api = authApi,
        requestContract = authRequestContract,
        registrationContract = registrationRequestContract,
        apiCallExecutor = apiCallExecutor
    )
    private val adminDashboardRemoteDataSource: AdminDashboardRemoteDataSource = AdminDashboardRemoteDataSourceImpl(
        api = adminDashboardApi,
        contract = adminDashboardContract,
        apiCallExecutor = apiCallExecutor
    )
    private val adminOrdersRemoteDataSource: AdminOrdersRemoteDataSource = AdminOrdersRemoteDataSourceImpl(
        api = adminOrdersApi,
        contract = adminOrdersContract,
        apiCallExecutor = apiCallExecutor
    )
    private val deliveryPartnerRemoteDataSource: DeliveryPartnerRemoteDataSource =
        DeliveryPartnerRemoteDataSourceImpl(
            api = deliveryPartnerApi,
            contract = deliveryPartnerContract,
            executor = apiCallExecutor
        )
    private val productManagementRemoteDataSource: ProductManagementRemoteDataSource =
        ProductManagementRemoteDataSourceImpl(
            api = productManagementApi,
            contract = productManagementContract,
            executor = apiCallExecutor
        )
    private val categoryManagementRemoteDataSource: CategoryManagementRemoteDataSource = CategoryManagementRemoteDataSourceImpl(categoryManagementApi, categoryManagementContract, apiCallExecutor)
    private val customerManagementRemoteDataSource: CustomerManagementRemoteDataSource = CustomerManagementRemoteDataSourceImpl(customerManagementApi, customerManagementContract, apiCallExecutor)
    private val deliveryDashboardDataSource: DeliveryDashboardDataSource = DeliveryDashboardDataSourceImpl(deliveryDashboardApi, deliveryDashboardContract, apiCallExecutor)

    val authRepository: AuthRepository = AuthRepositoryImpl(
        remoteDataSource = authRemoteDataSource,
        sessionManager = sessionManager
    )
    private val adminDashboardRepository: AdminDashboardRepository = AdminDashboardRepositoryImpl(
        remoteDataSource = adminDashboardRemoteDataSource
    )
    private val adminOrdersRepository: AdminOrdersRepository = AdminOrdersRepositoryImpl(
        remoteDataSource = adminOrdersRemoteDataSource
    )
    private val deliveryPartnerRepository: DeliveryPartnerRepository =
        DeliveryPartnerRepositoryImpl(deliveryPartnerRemoteDataSource)
    private val productManagementRepository: ProductManagementRepository =
        ProductManagementRepositoryImpl(productManagementRemoteDataSource)
    private val categoryManagementRepository: CategoryManagementRepository = CategoryManagementRepositoryImpl(categoryManagementRemoteDataSource)
    private val customerManagementRepository: CustomerManagementRepository = CustomerManagementRepositoryImpl(customerManagementRemoteDataSource)
    private val deliveryDashboardRepository: DeliveryDashboardRepository = DeliveryDashboardRepositoryImpl(deliveryDashboardDataSource)
    private val deliveryOrderWorkflowDataSource: DeliveryOrderWorkflowDataSource = DeliveryOrderWorkflowDataSourceImpl(deliveryOrderWorkflowApi, deliveryOrderWorkflowContract, apiCallExecutor)
    private val deliveryOrderWorkflowRepository: DeliveryOrderWorkflowRepository = DeliveryOrderWorkflowRepositoryImpl(deliveryOrderWorkflowDataSource)
    private val deliveryEarningsDataSource: DeliveryEarningsDataSource = DeliveryEarningsDataSourceImpl(deliveryEarningsApi, deliveryEarningsContract, apiCallExecutor)
    private val deliveryNotificationsDataSource = DeliveryNotificationsDataSource(deliveryNotificationsApi, deliveryNotificationsContract, apiCallExecutor)
    private val deliveryPartnerProfileDataSource: DeliveryPartnerProfileDataSource = DeliveryPartnerProfileDataSourceImpl(deliveryPartnerProfileApi, deliveryPartnerProfileContract, apiCallExecutor)
    private val deliveryAvailabilityDataSource: DeliveryAvailabilityDataSource = DeliveryAvailabilityDataSourceImpl(deliveryAvailabilityApi, deliveryAvailabilityContract, apiCallExecutor)
    private val deliveryEarningsRepository: DeliveryEarningsRepository = DeliveryEarningsRepositoryImpl(deliveryEarningsDataSource)
    private val deliveryNotificationsRepository: DeliveryNotificationsRepository = DeliveryNotificationsRepositoryImpl(deliveryNotificationsDataSource)
    private val deliveryPartnerProfileRepository: DeliveryPartnerProfileRepository = DeliveryPartnerProfileRepositoryImpl(deliveryPartnerProfileDataSource)
    private val deliveryAvailabilityRepository: DeliveryAvailabilityRepository = DeliveryAvailabilityRepositoryImpl(deliveryAvailabilityDataSource)
    var deliveryEarningsRepositoryOverride: DeliveryEarningsRepository? = null
    var deliveryNotificationsRepositoryOverride: DeliveryNotificationsRepository? = null
    var deliveryPartnerProfileRepositoryOverride: DeliveryPartnerProfileRepository? = null
    var deliveryAvailabilityRepositoryOverride: DeliveryAvailabilityRepository? = null
    var categoryManagementRepositoryOverride: CategoryManagementRepository? = null

    var adminDashboardRepositoryOverride: AdminDashboardRepository? = null
    var adminOrdersRepositoryOverride: AdminOrdersRepository? = null
    var deliveryPartnerRepositoryOverride: DeliveryPartnerRepository? = null
    var productManagementRepositoryOverride: ProductManagementRepository? = null
    var customerManagementRepositoryOverride: CustomerManagementRepository? = null
    var deliveryDashboardRepositoryOverride: DeliveryDashboardRepository? = null

    var deliveryOrderWorkflowRepositoryOverride: DeliveryOrderWorkflowRepository? = null

    val loginUseCase = LoginUseCase(authRepository)
    val registerUseCase = RegisterUseCase(authRepository)
    val restoreSessionUseCase = RestoreSessionUseCase(authRepository)
    val logoutUseCase = LogoutUseCase(authRepository)

    fun provideAdminDashboardUseCase(): GetAdminDashboardUseCase {
        return GetAdminDashboardUseCase(adminDashboardRepositoryOverride ?: adminDashboardRepository)
    }

    fun provideAdminOrdersUseCase(): GetAdminOrdersUseCase {
        return GetAdminOrdersUseCase(adminOrdersRepositoryOverride ?: adminOrdersRepository)
    }

    fun provideAdminOrderDetailsUseCase(): GetAdminOrderDetailsUseCase {
        return GetAdminOrderDetailsUseCase(adminOrdersRepositoryOverride ?: adminOrdersRepository)
    }

    fun provideUpdateAdminOrderStatusUseCase(): UpdateAdminOrderStatusUseCase {
        return UpdateAdminOrderStatusUseCase(adminOrdersRepositoryOverride ?: adminOrdersRepository)
    }

    fun provideCancelAdminOrderUseCase(): CancelAdminOrderUseCase {
        return CancelAdminOrderUseCase(adminOrdersRepositoryOverride ?: adminOrdersRepository)
    }

    fun provideDeliveryPartnersUseCase(): GetDeliveryPartnersUseCase =
        GetDeliveryPartnersUseCase(deliveryPartnerRepositoryOverride ?: deliveryPartnerRepository)

    fun provideDeliveryPartnerDetailsUseCase(): GetDeliveryPartnerDetailsUseCase =
        GetDeliveryPartnerDetailsUseCase(deliveryPartnerRepositoryOverride ?: deliveryPartnerRepository)

    fun provideUpdateDeliveryPartnerUseCase(): UpdateDeliveryPartnerUseCase =
        UpdateDeliveryPartnerUseCase(deliveryPartnerRepositoryOverride ?: deliveryPartnerRepository)

    fun provideGetCategoriesUseCase(): GetCategoriesUseCase = GetCategoriesUseCase(categoryManagementRepositoryOverride ?: categoryManagementRepository)
    fun provideGetCategoryDetailsUseCase(): GetCategoryDetailsUseCase = GetCategoryDetailsUseCase(categoryManagementRepositoryOverride ?: categoryManagementRepository)
    fun provideCreateCategoryUseCase(): CreateCategoryUseCase = CreateCategoryUseCase(categoryManagementRepositoryOverride ?: categoryManagementRepository)
    fun provideUpdateCategoryUseCase(): UpdateCategoryUseCase = UpdateCategoryUseCase(categoryManagementRepositoryOverride ?: categoryManagementRepository)
    fun providePerformCategoryActionUseCase(): PerformCategoryActionUseCase = PerformCategoryActionUseCase(categoryManagementRepositoryOverride ?: categoryManagementRepository)

    fun provideGetCustomersUseCase(): GetCustomersUseCase = GetCustomersUseCase(customerManagementRepositoryOverride ?: customerManagementRepository)
    fun provideGetCustomerDetailsUseCase(): GetCustomerDetailsUseCase = GetCustomerDetailsUseCase(customerManagementRepositoryOverride ?: customerManagementRepository)
    fun providePerformCustomerAdminActionUseCase(): PerformCustomerAdminActionUseCase = PerformCustomerAdminActionUseCase(customerManagementRepositoryOverride ?: customerManagementRepository)

    fun provideGetDeliveryDashboardUseCase(): GetDeliveryDashboardUseCase = GetDeliveryDashboardUseCase(deliveryDashboardRepositoryOverride ?: deliveryDashboardRepository)
    fun provideGetAssignedDeliveryOrdersUseCase(): GetAssignedDeliveryOrdersUseCase = GetAssignedDeliveryOrdersUseCase(deliveryOrderWorkflowRepositoryOverride ?: deliveryOrderWorkflowRepository)
    fun provideGetDeliveryHistoryUseCase(): GetDeliveryHistoryUseCase = GetDeliveryHistoryUseCase(deliveryOrderWorkflowRepositoryOverride ?: deliveryOrderWorkflowRepository)
    fun provideGetDeliveryEarningsSummaryUseCase(): GetDeliveryEarningsSummaryUseCase = GetDeliveryEarningsSummaryUseCase(deliveryEarningsRepositoryOverride ?: deliveryEarningsRepository)
    fun provideGetDeliveryEarningsHistoryUseCase(): GetDeliveryEarningsHistoryUseCase = GetDeliveryEarningsHistoryUseCase(deliveryEarningsRepositoryOverride ?: deliveryEarningsRepository)
    fun provideGetDeliveryNotificationsUseCase(): GetDeliveryNotificationsUseCase = GetDeliveryNotificationsUseCase(deliveryNotificationsRepositoryOverride ?: deliveryNotificationsRepository)
    fun provideMarkDeliveryNotificationReadUseCase(): MarkDeliveryNotificationReadUseCase = MarkDeliveryNotificationReadUseCase(deliveryNotificationsRepositoryOverride ?: deliveryNotificationsRepository)
    fun provideMarkAllDeliveryNotificationsReadUseCase(): MarkAllDeliveryNotificationsReadUseCase = MarkAllDeliveryNotificationsReadUseCase(deliveryNotificationsRepositoryOverride ?: deliveryNotificationsRepository)
    fun provideGetDeliveryPartnerProfileUseCase(): GetDeliveryPartnerProfileUseCase = GetDeliveryPartnerProfileUseCase(deliveryPartnerProfileRepositoryOverride ?: deliveryPartnerProfileRepository)
    fun provideUpdateDeliveryPartnerProfileUseCase(): UpdateDeliveryPartnerProfileUseCase = UpdateDeliveryPartnerProfileUseCase(deliveryPartnerProfileRepositoryOverride ?: deliveryPartnerProfileRepository)
    fun provideGetDeliveryAvailabilityUseCase(): GetDeliveryAvailabilityUseCase = GetDeliveryAvailabilityUseCase(deliveryAvailabilityRepositoryOverride ?: deliveryAvailabilityRepository)
    fun provideUpdateDeliveryAvailabilityUseCase(): UpdateDeliveryAvailabilityUseCase = UpdateDeliveryAvailabilityUseCase(deliveryAvailabilityRepositoryOverride ?: deliveryAvailabilityRepository)
    fun provideGetDeliveryOrderDetailsUseCase(): GetDeliveryOrderDetailsUseCase = GetDeliveryOrderDetailsUseCase(deliveryOrderWorkflowRepositoryOverride ?: deliveryOrderWorkflowRepository)
    fun providePerformDeliveryOrderActionUseCase(): PerformDeliveryOrderActionUseCase = PerformDeliveryOrderActionUseCase(deliveryOrderWorkflowRepositoryOverride ?: deliveryOrderWorkflowRepository)

    fun provideGetProductsUseCase(): GetProductsUseCase =
        GetProductsUseCase(productManagementRepositoryOverride ?: productManagementRepository)

    fun provideGetProductDetailsUseCase(): GetProductDetailsUseCase =
        GetProductDetailsUseCase(productManagementRepositoryOverride ?: productManagementRepository)

    fun provideGetProductCategoryOptionsUseCase(): GetProductCategoryOptionsUseCase =
        GetProductCategoryOptionsUseCase(productManagementRepositoryOverride ?: productManagementRepository)

    fun provideCreateProductUseCase(): CreateProductUseCase =
        CreateProductUseCase(productManagementRepositoryOverride ?: productManagementRepository)

    fun provideUpdateProductUseCase(): UpdateProductUseCase =
        UpdateProductUseCase(productManagementRepositoryOverride ?: productManagementRepository)


    fun provideUploadProductImageUseCase(): UploadProductImageUseCase =
        UploadProductImageUseCase(productManagementRepositoryOverride ?: productManagementRepository)

    fun providePerformProductAdminActionUseCase(): PerformProductAdminActionUseCase =
        PerformProductAdminActionUseCase(productManagementRepositoryOverride ?: productManagementRepository)
}
