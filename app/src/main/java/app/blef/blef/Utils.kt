package app.blef.blef

import android.app.Activity
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

val client = OkHttpClient()

fun Activity.queryEngine(activity: Int, url: String, doWithResponse: (response: Response) -> Unit) {
    val request = Request.Builder().url(url).build()
    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            e.printStackTrace()
        }
        override fun onResponse(call: Call, response: Response) {
            response.use {
                if (!response.isSuccessful) {
                    val engineErrorBar = Snackbar.make(findViewById(activity), JSONObject(response.body!!.string()).getString("error"), 3000)
                    engineErrorBar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).maxLines = 5
                    engineErrorBar.show()
                } else {
                    doWithResponse(response)
                }
            }
        }
    })
}

const val baseUrl = "https://n4p6oovxsg.execute-api.eu-west-2.amazonaws.com/games"

object redirectReasons {
    const val INVALID_UUID = 0
    const val ENGINE_DOWN = 1
    const val GAME_UNAVAILABLE = 2
}

object GameStatuses {
    const val NOT_STARTED = "Not started"
    const val RUNNING = "Running"
    const val FINISHED = "Finished"
}
