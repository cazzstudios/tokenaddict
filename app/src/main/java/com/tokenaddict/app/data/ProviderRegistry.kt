package com.tokenaddict.app.data

class ProviderRegistry private constructor() {
    private val providers = mutableMapOf<String, AiProvider>()

    fun register(provider: AiProvider) {
        providers[provider.id] = provider
    }

    fun getProvider(id: String): AiProvider? = providers[id]

    fun getAllProviders(): List<AiProvider> = providers.values.toList()

    companion object {
        @Volatile
        private var instance: ProviderRegistry? = null

        fun getInstance(): ProviderRegistry {
            return instance ?: synchronized(this) {
                instance ?: ProviderRegistry().also { instance = it }
            }
        }
    }
}
