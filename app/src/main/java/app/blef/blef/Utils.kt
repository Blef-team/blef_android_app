package app.blef.blef

import android.app.Activity
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import okhttp3.Response
import org.json.JSONObject

fun Activity.showEngineError(activity: Int, response: Response) {
    val engineErrorBar = Snackbar.make(findViewById(activity), JSONObject(response.body!!.string()).getString("error"), 3000)
    engineErrorBar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).maxLines = 5
    engineErrorBar.show()
}

const val baseUrl = "https://n4p6oovxsg.execute-api.eu-west-2.amazonaws.com/games"
