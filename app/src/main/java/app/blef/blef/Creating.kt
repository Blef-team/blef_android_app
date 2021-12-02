//  Created by Maciej Pomykala on 28.06.2021
//  Copyright © 2021 Blef Team.

package app.blef.blef

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import com.google.android.material.snackbar.Snackbar
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class Creating : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_creating)

        val sharedPref = this.getSharedPreferences("app.blef.blef.MAIN", Context.MODE_PRIVATE)

        val nicknameEdittext = findViewById<EditText>(R.id.create_nickname)
        nicknameEdittext.setText(sharedPref.getString("preferred_nickname", ""))
        nicknameEdittext.requestFocus()

        findViewById<Button>(R.id.create_generate_nickname).setOnClickListener {
            nicknameEdittext.setText(generateNickname())
        }

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(nicknameEdittext, 0)

        findViewById<Button>(R.id.create_create_button).setOnClickListener {
            val rawNickname = nicknameEdittext.text.toString()
            val nickname = rawNickname.replace(" ", "_")
            with (sharedPref.edit()) {
                putString("preferred_nickname", rawNickname)
                apply()
            }

            val mHandler = Handler(Looper.getMainLooper())
            val client = OkHttpClient()
            val request1 = Request.Builder()
                .url("$baseUrl/create")
                .build()
            client.newCall(request1).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            val engineErrorBar = Snackbar.make(findViewById(R.id.activity_creating), response.body!!.string(), 3000)
                            engineErrorBar.show()
                        } else {
                            val jsonBody1 = JSONObject(response.body!!.string())
                            val gameUuid = jsonBody1.getString("game_uuid")
                            val request2 = Request.Builder()
                                .url("$baseUrl/$gameUuid/join?nickname=$nickname")
                                .build()
                            client.newCall(request2).enqueue(object : Callback {
                                override fun onFailure(call: Call, e: IOException) {
                                    e.printStackTrace()
                                }
                                override fun onResponse(call: Call, response: Response) {
                                    response.use {
                                        if (!response.isSuccessful) {
                                            showEngineError(R.id.activity_creating, response)
                                        } else {
                                            val playerUuid = JSONObject(response.body!!.string()).getString("player_uuid")
                                            sharedPref.edit().putString("preferred_nickname", rawNickname)
                                            this@Creating.getSharedPreferences("app.blef.blef.PLAYER_UUID", Context.MODE_PRIVATE)
                                                .edit().putString(gameUuid, playerUuid).apply()
                                            this@Creating.getSharedPreferences("app.blef.blef.NICKNAME", Context.MODE_PRIVATE)
                                                .edit().putString(gameUuid, nickname).apply()
                                            val intent = Intent(this@Creating, Game::class.java).putExtra("game_uuid", gameUuid)
                                            mHandler.post{startActivity(intent)}
                                        }
                                    }
                                }
                            })
                        }
                    }
                }
            })
        }
    }
}