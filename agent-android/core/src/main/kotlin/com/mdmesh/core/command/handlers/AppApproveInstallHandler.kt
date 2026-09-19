package com.mdmesh.core.command.handlers

import com.mdmesh.core.command.CommandHandler
import com.mdmesh.core.command.CommandResults
import com.mdmesh.core.install.InstallManager
import com.mdmesh.core.install.InstallOutcome
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandResult
import com.mdmesh.proto.ProtocolJson
import kotlinx.serialization.Serializable

/** `app.approveInstall` — un-suspend a package the agent auto-suspended pending admin approval
 *  after a non-MDM-initiated install. Payload: `{ packageName }`. */
class AppApproveInstallHandler(
    private val installManager: InstallManager,
) : CommandHandler {

    override val type: String = "app.approveInstall"

    @Serializable
    private data class Payload(val packageName: String)

    override suspend fun handle(command: CommandEnvelope): CommandResult {
        val payload = command.payload
            ?: return CommandResults.failed(command, "app.approveInstall requires a payload")
        val p = runCatching {
            ProtocolJson.json.decodeFromJsonElement(Payload.serializer(), payload)
        }.getOrElse { return CommandResults.failed(command, "bad payload: ${it.message}") }

        return when (val outcome = installManager.setSuspended(p.packageName, false)) {
            InstallOutcome.Success -> CommandResults.done(command)
            is InstallOutcome.Skipped -> CommandResults.done(command, "skipped: ${outcome.reason}")
            is InstallOutcome.Failure -> CommandResults.failed(command, outcome.reason)
        }
    }
}
