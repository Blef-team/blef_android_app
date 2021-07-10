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
import com.google.android.material.snackbar.Snackbar
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class JoiningWithUuid : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_joining_with_uuid)

        val linkData: Uri? = intent?.data

        val gameUuid = if (linkData != null) linkData.toString().removePrefix("http://blef.app/") else intent.getStringExtra(
            game_uuid
        )

        val uuid_text = findViewById<TextView>(R.id.join_with_uuid_uuid_text)
        uuid_text.text = "You are joining game $gameUuid"

        val nicknameEdittext = findViewById<EditText>(R.id.join_with_uuid_nickname)
        nicknameEdittext.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(nicknameEdittext, 0)

        findViewById<Button>(R.id.join_with_uuid_join_button).setOnClickListener {
            val nickname = nicknameEdittext.text.toString()
            val mHandler = Handler(Looper.getMainLooper())
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://n4p6oovxsg.execute-api.eu-west-2.amazonaws.com/games/$gameUuid/join?nickname=$nickname")
                .build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            val engineErrorBar = Snackbar.make(findViewById(R.id.activity_joining_with_uuid), response.body!!.string(), 3000)
                            engineErrorBar.show()
                        } else {
                            val jsonBody = JSONObject(response.body!!.string())
                            val playerUuid = jsonBody.getString("player_uuid")
                            val intent = Intent(this@JoiningWithUuid, Game::class.java).apply {
                                putExtra("game_uuid", gameUuid)
                                putExtra("player_uuid", playerUuid)
                                putExtra("nickname", nickname)
                            }
                            mHandler.post{startActivity(intent)}
                        }
                    }
                }
            })
        }

        findViewById<Button>(R.id.join_with_uuid_observe_button).setOnClickListener {
            val intent = Intent(this, Game::class.java).apply {
                putExtra("game_uuid", gameUuid)
            }
            startActivity(intent)
        }
    }
}