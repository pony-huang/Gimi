package github.ponyhuang.gimi.data.modelaccess.shared

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** 厂商官方工具 API 测试共用夹具：短路一切请求的 canned HTTP client。 */
fun cannedClient(
    code: Int,
    body: String,
    onRequest: (Request) -> Unit = {},
): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        onRequest(chain.request())
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("canned")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
    .build()
