package com.phantomcode.app.data.git

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/** Abre assets de Releases privadas usando o token já guardado no Keystore. */
object GithubAssetClient {
    private const val REPOSITORY = "VSFLima/phantom-code"

    fun openReleaseAsset(tag: String, assetName: String, token: String): HttpURLConnection {
        val metadata = (URL("https://api.github.com/repos/$REPOSITORY/releases/tags/$tag").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        val metadataBody = metadata.inputStream.use { it.bufferedReader().readText() }
        metadata.disconnect()

        val assets = JSONObject(metadataBody).optJSONArray("assets") ?: JSONArray()
        var assetUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name") == assetName) {
                assetUrl = asset.optString("url")
                break
            }
        }
        val url = assetUrl?.takeIf { it.isNotBlank() }
            ?: error("Asset $assetName não encontrado na Release $tag")

        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
    }
}
