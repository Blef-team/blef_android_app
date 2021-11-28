//  Created by Maciej Pomykala on 28.06.2021
//  Copyright © 2021 Blef Team.

package app.blef.blef

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import java.io.IOException

class JoiningWithUuid : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_joining_with_uuid)

        val linkData: Uri? = intent?.data
        val matcher = "\\b[0-9a-f]{8}\\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\\b[0-9a-f]{12}\\b".toRegex()
        val gameUuid = matcher.find(linkData.toString())?.value

        if(linkData != null && gameUuid != "") {
            val mHandler = Handler(Looper.getMainLooper())
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://n4p6oovxsg.execute-api.eu-west-2.amazonaws.com/games/$gameUuid")
                .build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val intent = Intent(this@JoiningWithUuid, MainActivity::class.java)
                        .putExtra("reason", "Invite failed")
                    mHandler.post{startActivity(intent)}
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            val intent = Intent(this@JoiningWithUuid, MainActivity::class.java)
                                .putExtra("reason", "Invite failed")
                            mHandler.post{startActivity(intent)}
                        } else {
                            val intent = Intent(this@JoiningWithUuid, Game::class.java)
                                .putExtra("game_uuid", gameUuid)
                            mHandler.post{startActivity(intent)}
                        }
                    }
                }
            })
        } else {
            val intent = Intent(this@JoiningWithUuid, MainActivity::class.java)
                .putExtra("reason", "Invite failed")
            startActivity(intent)
        }
    }
}