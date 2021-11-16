//  Created by Maciej Pomykala on 27.06.2021
//  Copyright © 2021 Blef Team.

package app.blef.blef

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.skydoves.powerspinner.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.stream.Collectors
import kotlin.concurrent.fixedRateTimer

val sets = listOf(
    "High card, 9",
    "High card, 10",
    "High card, J",
    "High card, Q",
    "High card, K",
    "High card, A",
    "Pair of 9s",
    "Pair of 10s",
    "Pair of Js",
    "Pair of Qs",
    "Pair of Ks",
    "Pair of As",
    "Two pair, 10s and 9s",
    "Two pair, Js and 9s",
    "Two pair, Js and 10s",
    "Two pair, Qs and 9s",
    "Two pair, Qs and 10s",
    "Two pair, Qs and Js",
    "Two pair, Ks and 9s",
    "Two pair, Ks and 10s",
    "Two pair, Ks and Js",
    "Two pair, Ks and Qs",
    "Two pair, As and 9s",
    "Two pair, As and 10s",
    "Two pair, As and Js",
    "Two pair, As and Qs",
    "Two pair, As and Ks",
    "Small straight (9-K)",
    "Big straight (10-A)",
    "Great straight (9-A)",
    "Three of a kind, 9s",
    "Three of a kind, 10s",
    "Three of a kind, Js",
    "Three of a kind, Qs",
    "Three of a kind, Ks",
    "Three of a kind, As",
    "Full house, 9s over 10s",
    "Full house, 9s over Js",
    "Full house, 9s over Qs",
    "Full house, 9s over Ks",
    "Full house, 9s over As",
    "Full house, 10s over 9s",
    "Full house, 10s over Js",
    "Full house, 10s over Qs",
    "Full house, 10s over Ks",
    "Full house, 10s over As",
    "Full house, Js over 9s",
    "Full house, Js over 10s",
    "Full house, Js over Qs",
    "Full house, Js over Ks",
    "Full house, Js over As",
    "Full house, Qs over 9s",
    "Full house, Qs over 10s",
    "Full house, Qs over Js",
    "Full house, Qs over Ks",
    "Full house, Qs over As",
    "Full house, Ks over 9s",
    "Full house, Ks over 10s",
    "Full house, Ks over Js",
    "Full house, Ks over Qs",
    "Full house, Ks over As",
    "Full house, As over 9s",
    "Full house, As over 10s",
    "Full house, As over Js",
    "Full house, As over Qs",
    "Full house, As over Ks",
    "Flush ♣",
    "Flush ♦",
    "Flush ♥",
    "Flush ♠",
    "Four of a kind, 9s",
    "Four of a kind, 10s",
    "Four of a kind, Js",
    "Four of a kind, Qs",
    "Four of a kind, Ks",
    "Four of a kind, As",
    "Small straight flush (9-K) ♣",
    "Small straight flush (9-K) ♦",
    "Small straight flush (9-K) ♥",
    "Small straight flush (9-K) ♠",
    "Big straight flush (10-A) ♣",
    "Big straight flush (10-A) ♦",
    "Big straight flush (10-A) ♥",
    "Big straight flush (10-A) ♠",
    "Great straight flush (9-A) ♣",
    "Great straight flush (9-A) ♦",
    "Great straight flush (9-A) ♥",
    "Great straight flush (9-A) ♠"
)

class Card(val value: Int, val suit: Int) {}

class Game : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        val pixelDensity = resources.displayMetrics.density
        fun adjustForDensity(raw: Int): Int {
            return((raw * pixelDensity).toInt())
        }

        val gameUuid = intent.getStringExtra("game_uuid").toString().lowercase()
        val playerUuid = intent.getStringExtra("player_uuid")
        val nickname = intent.getStringExtra("nickname")

        val sharedPref = this.getSharedPreferences("app.blef.blef.MAIN", Context.MODE_PRIVATE)
        with (sharedPref.edit()) {
            putString("game_uuid", gameUuid)
            putString("player_uuid", playerUuid)
            putString("preferred_nickname", nickname)
            putString("nickname", nickname)
            apply()
        }

        class ProperMutableLiveData<T> : MutableLiveData<T>() {
            override fun setValue(value: T?) {
                if (value != this.value) {
                    super.setValue(value)
                }
            }
        }
        val message = ProperMutableLiveData<String>()
        val updateOnHold = MutableLiveData<Boolean>()
        updateOnHold.value = false
        val gameFinished = MutableLiveData<Boolean>()
        gameFinished.value = false

        val mHandler = Handler(Looper.getMainLooper())
        val client = OkHttpClient()
        val baseUrl = "https://n4p6oovxsg.execute-api.eu-west-2.amazonaws.com/games"
        val cardSizeHTML = "width=\"24%\""

        val emptyCardHTML = "<img src=\"cardEmpty.png\" $cardSizeHTML>"
        val questionCardHTML = "<img src=\"cardQuestion.png\" $cardSizeHTML>"

        fun updateGame() {
            val queryUrl = baseUrl + if (playerUuid != null)  "/$gameUuid?player_uuid=$playerUuid" else "/$gameUuid"
            val request = Request.Builder()
                .url(queryUrl)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            showEngineError(R.id.activity_game, response)
                        } else {
                            val newMessage = response.body!!.string()
                            if (newMessage != message.value.toString()) {
                                val newJson = JSONObject(newMessage)
                                if (newJson.getString("round_number").toInt() <= 1 || // hard update if game not started or in first round
                                    (newJson.getString("status") == "Running" && // hard update if game has not progressed to another round and has not finished
                                    newJson.getString("round_number") == JSONObject(message.value.toString()).getString("round_number"))) {
                                    mHandler.post{message.setValue(newMessage)}
                                } else {
                                    if (newJson.getString("status") == "Finished") mHandler.post{gameFinished.value = true}
                                    // Update only the current round
                                    val currentRound = JSONObject(message.value.toString()).getString("round_number")
                                    val queryUrl2 = baseUrl + if (playerUuid != null)  "/$gameUuid?player_uuid=$playerUuid&round=$currentRound" else "/$gameUuid?round=$currentRound"
                                    val request2 = Request.Builder()
                                        .url(queryUrl2)
                                        .build()

                                    client.newCall(request2).enqueue(object : Callback {
                                        override fun onFailure(call: Call, e: IOException) {
                                            e.printStackTrace()
                                        }

                                        override fun onResponse(call: Call, response: Response) {
                                            response.use {
                                                if (!response.isSuccessful) {
                                                    showEngineError(R.id.activity_game, response)
                                                } else {
                                                    val newMessage2 = response.body!!.string()
                                                    mHandler.post{
                                                        updateOnHold.value = true
                                                        message.setValue(newMessage2)
                                                    }
                                                }
                                            }
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
            })
        }

        fun hardUpdateGame() {
            val queryUrl = baseUrl + if (playerUuid != null)  "/$gameUuid?player_uuid=$playerUuid" else "/$gameUuid"
            val request = Request.Builder()
                .url(queryUrl)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            showEngineError(R.id.activity_game, response)
                        } else {
                            val newMessage = response.body!!.string()
                            if (newMessage != message.value.toString()) {
                                mHandler.post{
                                    updateOnHold.value = false
                                    message.setValue(newMessage)
                                }
                            }
                        }
                    }
                }
            })
        }

        fun updateGameIfEngineHappy(response: Response) {
            if (!response.isSuccessful) {
                val engineErrorBar = Snackbar.make(findViewById(R.id.activity_game), response.body!!.string(), 3000)
                engineErrorBar.show()
            } else {
                updateGame()
            }
        }

        fun makePublic() {
            val queryUrl = "$baseUrl/$gameUuid/make-public?admin_uuid=$playerUuid"
            val request = Request.Builder()
                .url(queryUrl)
                .build()
            client.newCall(request).enqueue(object : Callback{
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }
                override fun onResponse(call: Call, response: Response) {
                    updateGameIfEngineHappy(response)
                }
            })
        }

        fun makePrivate() {
            val queryUrl = "$baseUrl/$gameUuid/make-private?admin_uuid=$playerUuid"
            val request = Request.Builder()
                .url(queryUrl)
                .build()
            client.newCall(request).enqueue(object : Callback{
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }
                override fun onResponse(call: Call, response: Response) {
                    updateGameIfEngineHappy(response)
                }
            })
        }

        fun start() {
            val queryUrl = "$baseUrl/$gameUuid/start?admin_uuid=$playerUuid"
            val request = Request.Builder()
                .url(queryUrl)
                .build()
            client.newCall(request).enqueue(object : Callback{
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }
                override fun onResponse(call: Call, response: Response) {
                    updateGameIfEngineHappy(response)
                }
            })
        }

        fun makeCell(s: String): String {
            return("<td>".plus(s).plus("</td>"))
        }
        fun makeCentreCell(s: String): String {
            return("<td class=\"centreCell\">".plus(s).plus("</td>"))
        }
        fun makeLeftCell(s: String): String {
            return("<td class=\"leftCell\">".plus(s).plus("</td>"))
        }
        fun makeRightCell(s: String): String {
            return("<td class=\"rightCell\">".plus(s).plus("</td>"))
        }
        fun makeRow(vararg ss: String): String {
            var out = "<tr>"
            for (s in ss) out += s
            return(out.plus("</tr>"))
        }
        fun makeInfoTable(vararg ss: String): String {
            var out = "<table class=\"infoTable\"><tbody>"
            for (s in ss) out += s
            return(out.plus("</tbody></table>"))
        }
        fun makeHalfTable(vararg ss: String): String {
            var out = "<table class=\"halfTable\"><tbody>"
            for (s in ss) out += s
            return(out.plus("</tbody></table>"))
        }
        fun makeFullTable(vararg ss: String): String {
            var out = "<table class=\"fullTable\"><tbody>"
            for (s in ss) out += s
            return(out.plus("</tbody></table>"))
        }
        fun addOpenStyle(s: String): String {
            return("<style>".plus(assets.open("open_style.css").bufferedReader().lines().collect(Collectors.joining())).plus("</style>").plus(s))
        }
        fun addCardTableStyle(s: String): String {
            return("<style>".plus(assets.open("card_table_style.css").bufferedReader().lines().collect(Collectors.joining())).plus("</style>").plus(s))
        }
        fun formatNickname(raw: String, own: String?): String {
            val formatted = (if (raw == own) "<b>$raw</b>" else raw).replace("_", " ")
            return(formatted)
        }

        val gi = findViewById<LinearLayout>(R.id.gameInfo)
        val generalInfo = WebView(this@Game)
        generalInfo.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        generalInfo.tag = "generalInfo"
        generalInfo.isVerticalScrollBarEnabled = false
        val playersInfo = WebView(this@Game)
        playersInfo.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        playersInfo.isVerticalScrollBarEnabled = false
        playersInfo.tag = "playersInfo"
        val historyIntro = TextView(this@Game)
        historyIntro.tag = "historyIntro"
        val historyInfo = WebView(this@Game)
        historyInfo.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        historyInfo.isVerticalScrollBarEnabled = false
        historyInfo.tag = "historyInfo"
        val loserInfo = WebView(this@Game)
        loserInfo.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        loserInfo.isVerticalScrollBarEnabled = false
        loserInfo.tag = "loserInfo"

        fun generatePreStartGameInfo(gameObject: JSONObject, gi: LinearLayout) {
            val generalInfoData = makeInfoTable(
                makeRow(makeLeftCell("UUID"), makeRightCell(gameUuid)),
                makeRow(makeLeftCell("Visibility"), makeRightCell(if (gameObject.getString("public") == "true") "Public" else "Private")),
                makeRow(makeLeftCell("Admin"), makeRightCell(formatNickname(gameObject.getString("admin_nickname"), nickname)))
            )
            generalInfo.loadDataWithBaseURL("file:///android_asset/", addOpenStyle(generalInfoData), "text/html", "UTF-8", null)

            var playersInfoData = "<br><img src=\"players.png\" width=\"48\" height=\"48\"><br><br>"
            val playersArray = gameObject.getJSONArray("players")
            for (i in 0 until playersArray.length()) {
                playersInfoData = playersInfoData
                    .plus(formatNickname(playersArray.getJSONObject(i).getString("nickname"), nickname)).plus("<br>")
            }
            playersInfoData = addOpenStyle(makeInfoTable(makeRow(makeCentreCell(playersInfoData))))
            playersInfo.loadDataWithBaseURL("file:///android_asset/", playersInfoData, "text/html", "UTF-8", null)

            if (gi.findViewWithTag<WebView>("generalInfo") == null) gi.addView(generalInfo)
            if (gi.findViewWithTag<WebView>("playersInfo") == null) gi.addView(playersInfo)
        }

        fun showClosedHand(handSize: Int, max: Int): String {
            var cardsData = ""
            for (j in 0 until max) {
                if (j < handSize) {
                    cardsData = cardsData.plus(questionCardHTML)
                } else {
                    cardsData = cardsData.plus(emptyCardHTML)
                }
            }
            return(cardsData)
        }

        fun showOpenHand(hand: JSONArray, max: Int): String {
            var cardsData = ""
            val cardsList = ArrayList<Card>()
            for (j in 0 until hand.length()) {
                cardsList.add(Card(hand.getJSONObject(j).getInt("value"), hand.getJSONObject(j).getInt("colour")))
            }
            val sortedList = cardsList.sortedWith(compareBy({ -it.value }, { -it.suit }))
            for (j in 0 until max) {
                if (j < sortedList.size) {
                    cardsData = cardsData.plus("<img src=\"cards/cropped/${sortedList[j].value}${sortedList[j].suit}.png\" $cardSizeHTML>")
                } else {
                    cardsData = cardsData.plus(emptyCardHTML)
                }
            }
            return(cardsData)
        }

        fun generateRunningGameInfo(gameObject: JSONObject, gi: LinearLayout) {
            val max = gameObject.getString("max_cards").toInt()
            var playersInfoData = ""
            val playersArray = gameObject.getJSONArray("players")
            val nPlayers = playersArray.length()
            val handsArray = gameObject.getJSONArray("hands")

            if (handsArray.length() == 0) {
                for (i in 0 until nPlayers) {
                    val iNickname = playersArray.getJSONObject(i).getString("nickname")
                    val iCards = playersArray.getJSONObject(i).getInt("n_cards")
                    val cardsData = showClosedHand(iCards, max)
                    if (nPlayers.mod(2) == 1 && i == nPlayers - 1) {
                        playersInfoData = playersInfoData.plus(makeFullTable(makeCell(
                            (formatNickname(iNickname, nickname)).plus("<br>").plus(cardsData)
                        )))
                    } else {
                        playersInfoData = playersInfoData.plus(makeHalfTable(makeCell(
                            (formatNickname(iNickname, nickname)).plus("<br>").plus(cardsData)
                        )))
                        if (i.mod(2) == 1) playersInfoData = playersInfoData.plus("<br>")
                    }
                }
            } else if (handsArray.length() == 1) {
                for (i in 0 until playersArray.length()) {
                    val iNickname = playersArray.getJSONObject(i).getString("nickname")
                    val iCards = playersArray.getJSONObject(i).getInt("n_cards")
                    val cardsData = when {
                            iCards == 0 -> "Lost"
                            iNickname == nickname -> showOpenHand(handsArray.getJSONObject(0).getJSONArray("hand"), max)
                            else -> showClosedHand(iCards, max)
                        }
                    if (nPlayers.mod(2) == 1 && i == nPlayers - 1) {
                        playersInfoData = playersInfoData.plus(makeFullTable(makeCell(
                            (formatNickname(iNickname, nickname)).plus("<br>").plus(cardsData)
                        )))
                    } else {
                        playersInfoData = playersInfoData.plus(makeHalfTable(makeCell(
                            (formatNickname(iNickname, nickname)).plus("<br>").plus(cardsData)
                        )))
                        if (i.mod(2) == 1) playersInfoData = playersInfoData.plus("<br>")
                    }
                }
            } else {
                val activeNicknames = ArrayList<String>()
                for (i in 0 until handsArray.length()) activeNicknames.add(handsArray.getJSONObject(i).getString("nickname"))

                for (i in 0 until playersArray.length()) {
                    val iNickname = playersArray.getJSONObject(i).getString("nickname")
                    val cardsData =
                        if (activeNicknames.contains(iNickname)) showOpenHand(handsArray.getJSONObject(activeNicknames.indexOf(iNickname)).getJSONArray("hand"), max)
                        else showClosedHand(0, max)
                    if (nPlayers.mod(2) == 1 && i == nPlayers - 1) {
                        playersInfoData = playersInfoData.plus(makeFullTable(makeCell(
                            (formatNickname(iNickname, nickname)).plus("<br>").plus(cardsData)
                        )))
                    } else {
                        playersInfoData = playersInfoData.plus(makeHalfTable(makeCell(
                            (formatNickname(iNickname, nickname)).plus("<br>").plus(cardsData)
                        )))
                        if (i.mod(2) == 1) playersInfoData = playersInfoData.plus("<br>")
                    }
                }
            }
            playersInfo.loadDataWithBaseURL("file:///android_asset/", addCardTableStyle(playersInfoData), "text/html", "UTF-8", null)

            var historyInfoData = ""
            val historyArray = gameObject.getJSONArray("history")

            val roundEnded = historyArray.length() > 0 && historyArray.getJSONObject(historyArray.length() - 1).getInt("action_id") == 89

            if (roundEnded) {
                val loser = historyArray.getJSONObject(historyArray.length() - 1).getString("player")
                val loserHTML = "<p class=\"loserInfo\"><img src=\"cardPlus.png\" width=\"40\">"
                    .plus(formatNickname(loser, nickname))
                    .plus("</p>")
                loserInfo.loadDataWithBaseURL("file:///android_asset/", addOpenStyle(loserHTML), "text/html", "UTF-8", null)
            } else {
                val cp = gameObject.getString("cp_nickname")
                historyInfoData = historyInfoData.plus(makeRow(
                    makeLeftCell(formatNickname(cp, nickname)),
                    makeRightCell("...")
                ))
                loserInfo.loadDataWithBaseURL("file:///android_asset/", "", "text/html", "UTF-8", null)
            }

            if (historyArray.length() > 0) {
                for (i in historyArray.length() - 1 downTo 0) {
                    val iNickname = historyArray.getJSONObject(i).getString("player")
                    val iAction = historyArray.getJSONObject(i).getInt("action_id")
                    if (iAction <= 88) {
                        historyInfoData = historyInfoData.plus(makeRow(
                            makeLeftCell(formatNickname(iNickname, nickname)),
                            makeRightCell(if (iAction <= 87) sets[iAction] else "Check")
                        ))
                    }
                }
            }
            historyInfo.loadDataWithBaseURL("file:///android_asset/", addOpenStyle(makeInfoTable(historyInfoData)), "text/html", "UTF-8", null)

            if (gi.findViewWithTag<WebView>("playersInfo") == null) gi.addView(playersInfo)
            if (gi.findViewWithTag<TextView>("loserInfo") == null) gi.addView(loserInfo)
            if (gi.findViewWithTag<WebView>("historyInfo") == null) gi.addView(historyInfo)

            if (gi.findViewWithTag<TextView>("generalInfo") != null) gi.removeView(generalInfo)

            if (!roundEnded) {
                loserInfo.visibility = View.GONE
            } else {
                loserInfo.visibility = View.VISIBLE
            }
        }

        fun generateFinishedGameInfo(gameObject: JSONObject, gi: LinearLayout) {
            var playersInfoData = ""
            val playersArray = gameObject.getJSONArray("players")
            for (i in 0 until playersArray.length()) {
                val iNickname = playersArray.getJSONObject(i).getString("nickname")
                val iCards = playersArray.getJSONObject(i).getInt("n_cards")
                playersInfoData = playersInfoData.plus(makeRow(
                    makeLeftCell(formatNickname(iNickname, nickname)),
                    makeRightCell(if (iCards == 0) "" else "\uD83C\uDFC6")
                ))
            }
            playersInfo.loadDataWithBaseURL("file:///android_asset/", addOpenStyle(makeInfoTable(playersInfoData)), "text/html", "UTF-8", null)

            if (gi.findViewWithTag<WebView>("playersInfo") == null) gi.addView(playersInfo)

            if (gi.findViewWithTag<TextView>("generalInfo") != null) gi.removeView(generalInfo)
            if (gi.findViewWithTag<TextView>("loserInfo") != null) gi.removeView(loserInfo)
            if (gi.findViewWithTag<WebView>("historyInfo") != null) gi.removeView(historyInfo)
        }

        fun generateGameInfo(gameObject: JSONObject) {
            when {
                gameObject.getString("status") == "Not started" -> {
                    generatePreStartGameInfo(gameObject, gi)
                }
                gameObject.getString("status") == "Running" -> {
                    generateRunningGameInfo(gameObject, gi)
                }
                gameObject.getString("status") == "Finished" -> {
                    generateFinishedGameInfo(gameObject, gi)
                }
            }
        }

        fun sendAction(actionId: Int) {
            val queryUrl = "$baseUrl/$gameUuid/play?player_uuid=$playerUuid&action_id=$actionId"
            val request = Request.Builder()
                .url(queryUrl)
                .build()
            client.newCall(request).enqueue(object : Callback{
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }
                override fun onResponse(call: Call, response: Response) {
                    val newMessage = response.body!!.string()
                    val putUpdateOnHold = JSONObject(newMessage).isNull("cp_nickname")
                    mHandler.post{
                        updateOnHold.value = putUpdateOnHold
                        message.setValue(newMessage)
                    }
                }
            })
        }

        val singleButtonParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        singleButtonParams.setMargins(0, adjustForDensity(6), 0, adjustForDensity(6))
        val leftButtonParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        leftButtonParams.setMargins(0, adjustForDensity(6), adjustForDensity(6), adjustForDensity(6))
        val rightButtonParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        rightButtonParams.setMargins(adjustForDensity(6), adjustForDensity(6), 0, adjustForDensity(6))
        val verticalButtonParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        verticalButtonParams.setMargins(0, 0, 0, adjustForDensity(6))

        val confirmButton = MaterialButton(this@Game)
        confirmButton.text = "Confirm"
        confirmButton.height = adjustForDensity(80)
        confirmButton.setOnClickListener {
            sendAction(sets.indexOf(findViewById<PowerSpinnerView>(0).text))
        }

        val checkButton = MaterialButton(this@Game)
        checkButton.text = "Check"
        checkButton.height = adjustForDensity(80)
        checkButton.setOnClickListener {
            sendAction(88)
        }

        val startButton = MaterialButton(this@Game)
        startButton.tag = "start"
        startButton.text = "Start game"
        startButton.height = adjustForDensity(80)
        startButton.layoutParams = verticalButtonParams
        startButton.setOnClickListener{start()}

        val inviteButton = MaterialButton(this@Game)
        inviteButton.tag = "inviteButton"
        inviteButton.text = "Send invite"
        inviteButton.height = adjustForDensity(80)
        inviteButton.layoutParams = verticalButtonParams
        inviteButton.setOnClickListener{
            val link = "Join me for a game of Blef: https://blef.app/?link=http://blef.app/$gameUuid"
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, link)
            startActivity(Intent.createChooser(intent, "Share via"))
        }

        val publicPrivateButton = MaterialButton(this@Game)
        publicPrivateButton.height = adjustForDensity(80)
        publicPrivateButton.layoutParams = verticalButtonParams

        fun makeBetChooser(lastActionId: Int): PowerSpinnerView {
            val betChooser = createPowerSpinnerView(this) {
                setArrowTint(Color.parseColor("#000000"))
                setArrowPadding(12)
                setArrowAnimate(false)
                setArrowGravity(SpinnerGravity.END)
                setSpinnerPopupHeight((200 + resources.displayMetrics.heightPixels * 0.3).toInt())
                setSpinnerPopupAnimation(SpinnerAnimation.DROPDOWN)
                setSpinnerPopupAnimationStyle(android.R.style.Animation)
                setSpinnerPopupBackgroundColor(Color.parseColor("#ffffff"))
                setLifecycleOwner(this@Game)
            }
            betChooser.apply {
                setSpinnerAdapter(DefaultSpinnerAdapter(betChooser))
                setItems(sets.subList(lastActionId + 1, 88))
                selectItemByIndex(0)
                lifecycleOwner = this@Game
            }
            betChooser.id = 0
            betChooser.setPadding(adjustForDensity(12), adjustForDensity(8), adjustForDensity(12), adjustForDensity(8))
            betChooser.gravity = Gravity.CENTER_VERTICAL
            betChooser.setBackgroundResource(R.drawable.spinner_background)
            val sizeParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, adjustForDensity(70))
            sizeParams.setMargins(0, adjustForDensity(6), 0, 0)
            betChooser.layoutParams = sizeParams
            return(betChooser)
        }

        val confirmOrCheck = LinearLayout(this@Game)
        confirmOrCheck.orientation = LinearLayout.HORIZONTAL
        confirmOrCheck.isBaselineAligned = false

        val updateButton = MaterialButton(this@Game)
        updateButton.tag = "update"
        updateButton.text = "Go to current round"
        updateButton.height = adjustForDensity(80)
        updateButton.layoutParams = singleButtonParams
        updateButton.setOnClickListener{hardUpdateGame()}

        fun generateGameControls(gameObject: JSONObject) {
            val ll = findViewById<LinearLayout>(R.id.controlPanel)
            ll.removeAllViews()

            val history = gameObject.getJSONArray("history")

            if (updateOnHold.value == true) {
                ll.removeAllViews()
                ll.addView(updateButton)
            } else if (gameObject.getString("admin_nickname") == nickname && gameObject.getString("status") == "Not started") {
                ll.removeAllViews()
                ll.addView(startButton)
                if (gameObject.getString("public") == "false") {
                    publicPrivateButton.tag = "make_public"
                    publicPrivateButton.text = "Make public"
                    publicPrivateButton.setOnClickListener{makePublic()}
                } else {
                    publicPrivateButton.tag = "make_private"
                    publicPrivateButton.text = "Make private"
                    publicPrivateButton.setOnClickListener{makePrivate()}
                }
                ll.addView(publicPrivateButton)
            } else if (gameObject.getString("cp_nickname") == nickname) {
                // Time to move
                confirmOrCheck.removeAllViews()
                if (gameObject.getJSONArray("history").length() == 0) {
                    ll.addView(makeBetChooser(-1))
                    confirmOrCheck.addView(confirmButton)
                    confirmButton.layoutParams = singleButtonParams
                } else if (history.getJSONObject(history.length() - 1).getInt("action_id") in 0..86) {
                    val lastAction = history.getJSONObject(history.length() - 1).getInt("action_id")
                    ll.addView(makeBetChooser(lastAction))
                    confirmOrCheck.addView(confirmButton)
                    confirmOrCheck.addView(checkButton)
                    confirmButton.layoutParams = leftButtonParams
                    checkButton.layoutParams = rightButtonParams
                } else {
                    confirmOrCheck.addView(checkButton)
                    checkButton.layoutParams = singleButtonParams
                }
                ll.addView(confirmOrCheck)
            }

            if (gameObject.getString("status") == "Not started") {
                ll.addView(inviteButton)
            }
        }

        message.observe(this@Game, {
            val gameObject = JSONObject(message.value.toString())
            generateGameInfo(gameObject)
            generateGameControls(gameObject)

            if (gameObject.getString("status") == "Finished") {
                with (sharedPref.edit()) {
                    putString("game_uuid", "")
                    putString("player_uuid", "")
                    putString("preferred_nickname", "")
                    putString("nickname", "")
                    apply()
                }
            }
        })

        hardUpdateGame()

        fixedRateTimer("update_game", false, 0L, 1000) {
            if (hasWindowFocus() && gameFinished.value == false && updateOnHold.value == false) updateGame()
        }
    }

    override fun onBackPressed() {
        startActivity(Intent(this@Game, MainActivity::class.java))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        startActivity(Intent(this@Game, MainActivity::class.java))
        return(true)
    }
}

