//  Created by Maciej Pomykala on 28.06.2021
//  Copyright © 2021 Blef Team.

package app.blef.blef

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class JoiningWithUuid : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_joining_with_uuid)

        val linkData: Uri? = intent?.data
        val gameUuid = linkData?.toString()?.removePrefix("https://www.blef.app/")

        val sharedPref = this.getSharedPreferences("app.blef.blef.MAIN", Context.MODE_PRIVATE)

        val mHandler = Handler(Looper.getMainLooper())
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://n4p6oovxsg.execute-api.eu-west-2.amazonaws.com/games/$gameUuid")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val intent = Intent(this@JoiningWithUuid, MainActivity::class.java).apply {
                    putExtra("reason", "Invite failed")
                }
                mHandler.post{startActivity(intent)}
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        val intent = Intent(this@JoiningWithUuid, MainActivity::class.java).apply {
                            putExtra("reason", "Invite failed")
                        }
                        mHandler.post{startActivity(intent)}
                    } else {
                        if (sharedPref.getString("game_uuid", null) == gameUuid) {
                            val intent = Intent(this@JoiningWithUuid, Game::class.java).apply {
                                putExtra("game_uuid", sharedPref.getString("game_uuid", null))
                                putExtra("player_uuid", sharedPref.getString("player_uuid", null))
                                putExtra("nickname", sharedPref.getString("nickname", null))
                            }
                            mHandler.post{startActivity(intent)}
                        } else {
                            val intent = Intent(this@JoiningWithUuid, Game::class.java).apply {
                                putExtra("game_uuid", gameUuid)
                            }
                            mHandler.post{startActivity(intent)}
                        }
                    }
                }
            }
        })
    }
}