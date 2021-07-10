//  Created by Maciej Pomykala on 28.06.2021
//  Copyright © 2021 Blef Team.

package app.blef.blef

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import com.google.android.material.snackbar.Snackbar
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class JoiningWithoutUuid : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_joining_without_uuid)

        findViewById<Button>(R.id.join_without_uuid_join_button).setOnClickListener {
            val gameUuid = findViewById<EditText>(R.id.join_without_uuid_uuid).text.toString()
            val nickname = findViewById<EditText>(R.id.join_without_uuid_nickname).text.toString()
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
                            val engineErrorBar = Snackbar.make(findViewById(R.id.activity_joining_without_uuid), response.body!!.string(), 3000)
                            engineErrorBar.show()
                        } else {
                            val jsonBody = JSONObject(response.body!!.string())
                            val playerUuid = jsonBody.getString("player_uuid")
                            val intent = Intent(this@JoiningWithoutUuid, Game::class.java).apply {
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

        findViewById<Button>(R.id.join_without_uuid_observe_button).setOnClickListener {
            val gameUuid = findViewById<EditText>(R.id.join_without_uuid_uuid).text.toString()
            val intent = Intent(this, Game::class.java).apply {
                putExtra("game_uuid", gameUuid)
            }
            startActivity(intent)
        }
    }
}