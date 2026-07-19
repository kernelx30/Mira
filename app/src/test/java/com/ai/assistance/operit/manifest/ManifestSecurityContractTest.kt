package com.ai.assistance.operit.manifest

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class ManifestSecurityContractTest {
    private val debugOnlyReceivers =
        setOf(
            ".core.tools.javascript.ScriptExecutionReceiver",
            ".core.tools.packTool.ToolPkgDebugInstallReceiver",
            ".core.tools.packTool.PackageDebugRefreshReceiver",
            ".core.tools.packTool.ToolPkgComposeDslDebugDumpReceiver",
        )

    @Test
    fun debugReceiversAreAbsentFromMainAndExportedOnlyInDebug() {
        val mainReceivers = receivers(moduleFile("src/main/AndroidManifest.xml"))
        val debugReceivers = receivers(moduleFile("src/debug/AndroidManifest.xml"))

        debugOnlyReceivers.forEach { receiverName ->
            assertFalse(mainReceivers.containsKey(receiverName))
            assertEquals("true", debugReceivers[receiverName]?.androidAttribute("exported"))
        }
    }

    @Test
    fun exportedIntegrationReceiversHaveExpectedProtectionContracts() {
        val mainReceivers = receivers(moduleFile("src/main/AndroidManifest.xml"))
        val workflow = mainReceivers[".integrations.tasker.WorkflowTaskerReceiver"]
        val externalChat = mainReceivers[".integrations.intent.ExternalChatReceiver"]
        val shower = mainReceivers[".core.tools.agent.ShowerBinderReceiver"]

        assertNotNull(workflow)
        assertEquals("true", workflow?.androidAttribute("exported"))
        assertEquals(
            "\${applicationId}.permission.INTERNAL_AUTOMATION",
            workflow?.androidAttribute("permission"),
        )
        assertEquals("true", externalChat?.androidAttribute("exported"))
        assertEquals("true", shower?.androidAttribute("exported"))
    }

    @Test
    fun customTaskerReceiverDoesNotClaimLibraryFireSettingAction() {
        val workflow =
            receivers(moduleFile("src/main/AndroidManifest.xml"))
                .getValue(".integrations.tasker.WorkflowTaskerReceiver")
        val actions = workflow.getElementsByTagName("action")
        val values =
            (0 until actions.length)
                .map { actions.item(it) as Element }
                .map { it.androidAttribute("name") }

        assertTrue("com.ai.assistance.operit.TRIGGER_WORKFLOW" in values)
        assertFalse("com.twofortyfouram.locale.intent.action.FIRE_SETTING" in values)
    }

    @Test
    fun privateDataIsExcludedFromAndroidBackupAndDeviceTransfer() {
        val manifest = document(moduleFile("src/main/AndroidManifest.xml"))
        val application = manifest.getElementsByTagName("application").item(0) as Element

        assertEquals("false", application.androidAttribute("allowBackup"))
        assertEquals("@xml/data_extraction_rules", application.androidAttribute("dataExtractionRules"))
        assertEquals("@xml/backup_rules", application.androidAttribute("fullBackupContent"))

        val extractionRules = document(moduleFile("src/main/res/xml/data_extraction_rules.xml"))
        val cloudBackup = extractionRules.getElementsByTagName("cloud-backup").item(0) as Element
        val deviceTransfer = extractionRules.getElementsByTagName("device-transfer").item(0) as Element
        assertEquals(BACKUP_DOMAINS, excludeDomains(cloudBackup))
        assertEquals(BACKUP_DOMAINS, excludeDomains(deviceTransfer))

        val legacyRules = document(moduleFile("src/main/res/xml/backup_rules.xml"))
        assertEquals(BACKUP_DOMAINS, excludeDomains(legacyRules.documentElement))
    }

    private fun receivers(file: File): Map<String, Element> {
        val document = document(file)
        val nodes = document.getElementsByTagName("receiver")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
                .associateBy { it.androidAttribute("name") }
    }

    private fun document(file: File) =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)

    private fun excludeDomains(parent: Element): Set<String> {
        val excludes = parent.getElementsByTagName("exclude")
        return (0 until excludes.length)
            .map { excludes.item(it) as Element }
            .onEach { assertEquals(".", it.getAttribute("path")) }
            .mapTo(mutableSetOf()) { it.getAttribute("domain") }
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private fun moduleFile(relativePath: String): File =
        sequenceOf(File(relativePath), File("app", relativePath))
            .firstOrNull(File::isFile)
            ?: error("Missing test fixture: $relativePath")

    companion object {
        private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        private val BACKUP_DOMAINS =
            setOf(
                "root",
                "file",
                "database",
                "sharedpref",
                "external",
                "device_root",
                "device_file",
                "device_database",
                "device_sharedpref",
            )
    }
}
