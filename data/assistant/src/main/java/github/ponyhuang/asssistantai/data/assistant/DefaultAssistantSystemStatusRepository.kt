package github.ponyhuang.asssistantai.data.assistant

import android.app.role.RoleManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.asssistantai.domain.assistant.repository.AssistantSystemStatusRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAssistantSystemStatusRepository @Inject constructor(
    @ApplicationContext context: Context,
) : AssistantSystemStatusRepository {
    private val roleManager = context.getSystemService(RoleManager::class.java)

    override fun isAssistantRoleAvailable(): Boolean =
        roleManager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true

    override fun isDefaultAssistant(): Boolean =
        roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
}
