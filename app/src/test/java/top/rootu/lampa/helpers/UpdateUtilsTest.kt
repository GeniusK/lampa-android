package top.rootu.lampa.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.rootu.lampa.models.Asset

class UpdateUtilsTest {

    private fun asset(name: String, url: String = "https://host/$name") = Asset(
        url = url,
        id = 0,
        node_id = "",
        name = name,
        label = null,
        uploader = null,
        content_type = "application/vnd.android.package-archive",
        state = "uploaded",
        size = 0,
        download_count = 0,
        created_at = "",
        updated_at = "",
        browser_download_url = url,
    )

    @Test
    fun prefersFlavorApk() {
        val assets = listOf(
            asset("app-full-release.apk"),
            asset("app-lite-release.apk"),
        )
        assertEquals(
            "https://host/app-lite-release.apk",
            UpdateUtils.pickApkUrl(assets, "lite"),
        )
    }

    @Test
    fun fallsBackToFirstApkWhenFlavorMissing() {
        val assets = listOf(
            asset("readme.txt"),
            asset("app-something.apk"),
            asset("app-other.apk"),
        )
        assertEquals(
            "https://host/app-something.apk",
            UpdateUtils.pickApkUrl(assets, "lite"),
        )
    }

    @Test
    fun ignoresNonApkAssets() {
        // Regression: the old loop kept the LAST asset (a .json), never an apk.
        val assets = listOf(
            asset("app-lite-release.apk"),
            asset("update-lite.json"),
        )
        assertEquals(
            "https://host/app-lite-release.apk",
            UpdateUtils.pickApkUrl(assets, "lite"),
        )
    }

    @Test
    fun returnsNullWhenNoApk() {
        val assets = listOf(asset("update-lite.json"), asset("notes.txt"))
        assertNull(UpdateUtils.pickApkUrl(assets, "lite"))
    }

    @Test
    fun returnsNullForEmptyAssets() {
        assertNull(UpdateUtils.pickApkUrl(emptyList(), "lite"))
    }
}
