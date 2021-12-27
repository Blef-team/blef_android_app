package app.blef.blef

import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

open class BlefActivity : AppCompatActivity() {
    val client = OkHttpClient()

    val baseUrl = "https://n4p6oovxsg.execute-api.eu-west-2.amazonaws.com/games"

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

    fun showQueryError(activity: Int, message: String) {
        val engineErrorBar = Snackbar.make(findViewById(activity), message, 3000)
        engineErrorBar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).maxLines = 5
        engineErrorBar.show()
    }

    fun queryEngine(activity: Int, url: String, doWithResponse: (response: Response) -> Unit) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                showQueryError(activity, getString(R.string.engine_down))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        showQueryError(activity, JSONObject(response.body!!.string()).getString("error"))
                    }
                    else {
                        doWithResponse(response)
                    }
                }
            }
        })
    }

    fun queryEngineUsingButton(button: Button, temporaryText: String,
    activity: Int, url: String, doWithResponse: (response: Response) -> Unit) {
        val savedText = button.text
        button.text = temporaryText
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                button.text = savedText
                showQueryError(activity, getString(R.string.engine_down))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    button.text = savedText
                    if (!response.isSuccessful) {
                        showQueryError(activity, JSONObject(response.body!!.string()).getString("error"))
                    } else {
                        doWithResponse(response)
                    }
                }
            }
        })
    }
}