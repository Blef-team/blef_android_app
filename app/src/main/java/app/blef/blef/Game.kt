//  Created by Maciej Pomykala on 27.06.2021
//  Copyright © 2021 Blef Team.

package app.blef.blef

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonParser
import com.skydoves.powerspinner.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.stream.Collectors
import kotlin.concurrent.fixedRateTimer

class Game : AppCompatActivity() {
    class Card(val value: Int, val suit: Int)
    object GameStatuses {
        const val NOT_STARTED = "Not started"
        const val RUNNING = "Running"
        const val FINISHED = "Finished"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)
        val sets = Sets(this@Game)

        val pixelDensity = resources.displayMetrics.density
        fun adjustForDensity(raw: Int): Int {
            return((raw * pixelDensity).toInt())
        }

        class BlefButton(context: Context): MaterialButton(context) {
            init { height = adjustForDensity(80) }
        }

        val linkData: Uri? = intent?.data
        val matcher = "\\b[0-9a-f]{8}\\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\\b[0-9a-f]{12}\\b".toRegex()
        val linkUuid = matcher.find(linkData.toString())?.value
        if (linkData != null && linkUuid == null) {
            val intent = Intent(this@Game, MainActivity::class.java).putExtra("reason", redirectReasons.INVALID_UUID)
            startActivity(intent)
            finish()
            return
        }
        val gameUuid = if (linkData != null) linkUuid else intent.getStringExtra("game_uuid").toString().lowercase()

        val sharedPref = this.getSharedPreferences("app.blef.blef.MAIN", Context.MODE_PRIVATE)
        sharedPref.edit().putString("game_uuid", gameUuid).apply()
        val sharedPrefPlayerUuid = this.getSharedPreferences("app.blef.blef.PLAYER_UUID", Context.MODE_PRIVATE)
        var playerUuid = sharedPrefPlayerUuid.getString(gameUuid, null)
        val sharedPrefNickname = this.getSharedPreferences("app.blef.blef.NICKNAME", Context.MODE_PRIVATE)
        var nickname = sharedPrefNickname.getString(gameUuid, null)

        val message = MutableLiveData<String>()
        var handsTrigger = ""
        var updateOnHold = false
        var gameFinished = false

        val mHandler = Handler(Looper.getMainLooper())

        val initialRequest = Request.Builder().url(baseUrl + if (playerUuid != null)  "/$gameUuid?player_uuid=$playerUuid" else "/$gameUuid").build()
        client.newCall(initialRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val intent = Intent(this@Game, MainActivity::class.java).putExtra("reason", redirectReasons.ENGINE_DOWN)
                mHandler.post{
                    startActivity(intent)
                    finish()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        val intent = Intent(this@Game, MainActivity::class.java).putExtra("reason", redirectReasons.GAME_UNAVAILABLE)
                        mHandler.post{
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        val newMessage = response.body!!.string()
                        mHandler.post{
                            message.value = newMessage
                        }
                    }
                }
            }
        })

        val emptyCardHTML = "<img class=\"emptyCard\" src=\"cardEmpty2.png\">"
        val questionCardHTML = "<img class=\"questionCard\" src=\"cardQuestion.png\">"

        fun updateGame() {
            queryEngine(
                R.id.activity_game,
                baseUrl + if (playerUuid != null)  "/$gameUuid?player_uuid=$playerUuid" else "/$gameUuid",
            ) { response ->
                val newMessage = response.body!!.string()
                if (JsonParser().parse(newMessage) != JsonParser().parse(message.value.toString())) {
                    val newJson = JSONObject(newMessage)
                    if (newJson.getString("round_number").toInt() <= 1 || // hard update if game not started or in first round
                        (newJson.getString("status") == GameStatuses.RUNNING && // hard update if game has not progressed to another round and has not finished
                                newJson.getString("round_number") == JSONObject(message.value.toString()).getString("round_number"))) {
                        mHandler.post{message.setValue(newMessage)}
                    } else {
                        if (newJson.getString("status") == GameStatuses.FINISHED) mHandler.post{gameFinished = true}
                        // Update only the current round
                        val currentRound = JSONObject(message.value.toString()).getString("round_number")

                        queryEngine(
                            R.id.activity_game,
                            baseUrl + if (playerUuid != null)  "/$gameUuid?player_uuid=$playerUuid&round=$currentRound" else "/$gameUuid?round=$currentRound"
                        ) { response2 ->
                            val newMessage2 = response2.body!!.string()
                            mHandler.post {
                                updateOnHold = true
                                message.setValue(newMessage2)
                            }
                        }
                    }
                }

            }
        }

        fun hardUpdateGame() {
            queryEngine(
                R.id.activity_game,
                baseUrl + if (playerUuid != null)  "/$gameUuid?player_uuid=$playerUuid" else "/$gameUuid"
            ) { response ->
                val newMessage = response.body!!.string()
                if (newMessage != message.value.toString()) {
                    mHandler.post{
                        updateOnHold = false
                        message.setValue(newMessage)
                    }
                }
            }
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
            queryEngine(
                R.id.activity_game,
                "$baseUrl/$gameUuid/make-public?admin_uuid=$playerUuid",
                ::updateGameIfEngineHappy
            )
        }

        fun makePrivate() {
            queryEngine(
                R.id.activity_game,
                "$baseUrl/$gameUuid/make-private?admin_uuid=$playerUuid",
                ::updateGameIfEngineHappy
            )
        }

        fun start() {
            queryEngine(
                R.id.activity_game,
                "$baseUrl/$gameUuid/start?admin_uuid=$playerUuid",
                ::updateGameIfEngineHappy
            )
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
        fun formatNickname(raw: String, own: String?, active: Boolean = true): String {
            var formatted = if (raw == own) "<b>$raw</b>" else raw
            formatted = formatted.replace("_", " ")
            if (!active) formatted = "<s>$formatted</s>"
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
        class BetterWebViewClient : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, facIcon: Bitmap?) {
                playersInfo.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, playersInfo.height)
            }
            override fun onPageFinished(view: WebView, url: String) {
                playersInfo.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
        }
        playersInfo.webViewClient = BetterWebViewClient()
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
                makeRow(
                    makeLeftCell(getString(R.string.room)),
                    makeRightCell(if (gameObject.getString("public") == "true") gameObject.getString("room") else getString(R.string.room_private))
                ),
                makeRow(makeLeftCell(getString(R.string.admin)), makeRightCell(formatNickname(gameObject.getString("admin_nickname"), nickname)))
            )
            generalInfo.loadDataWithBaseURL("file:///android_asset/", addOpenStyle(generalInfoData), "text/html", "UTF-8", null)

            var playersInfoData = "<br><img src=\"players.png\" width=\"48\" height=\"48\"><br><br>"
            val playersArray = gameObject.getJSONArray("players")
            for (i in 0 until playersArray.length()) {
                playersInfoData = playersInfoData
                    .plus(formatNickname(playersArray.getJSONObject(i).getString("nickname"), nickname, true)).plus("<br>")
            }
            playersInfoData = addOpenStyle(makeInfoTable(makeRow(makeCentreCell(playersInfoData))))
            playersInfo.loadDataWithBaseURL("file:///android_asset/", playersInfoData, "text/html", "UTF-8", null)

            if (gi.findViewWithTag<WebView>("generalInfo") == null) gi.addView(generalInfo)
            if (gi.findViewWithTag<WebView>("playersInfo") == null) gi.addView(playersInfo)
        }

        fun showClosedHand(handSize: Int, max: Int): String {
            var cardsData = ""
            for (j in 0 until max) {
                cardsData = if (j < handSize) cardsData.plus(questionCardHTML) else cardsData.plus(emptyCardHTML)
            }
            return(cardsData)
        }

        fun showOpenHand(hand: JSONArray, max: Int, set: Int = -1, setComplete: Boolean = false): String {
            var cardsData = ""
            val cardsList = ArrayList<Card>()
            for (j in 0 until hand.length()) {
                cardsList.add(Card(hand.getJSONObject(j).getInt("value"), hand.getJSONObject(j).getInt("colour")))
            }
            val sortedList = cardsList.sortedWith(compareBy({ -it.value }, { -it.suit }))
            for (j in 0 until max) {
                cardsData = if (j < sortedList.size) {
                    val value = sortedList[j].value
                    val suit = sortedList[j].suit
                    val cardClass = when{
                        set != -1 && sets.checkIfContributes(value, suit, set) && setComplete -> "openCardSufficient"
                        set != -1 && sets.checkIfContributes(value, suit, set) && !setComplete ->"openCardInsufficient"
                        else -> "openCardNeutral"
                    }
                    cardsData.plus("<img class=\"$cardClass\" src=\"cards/cropped/$value$suit.png\">")
                } else {
                    cardsData.plus(emptyCardHTML)
                }
            }
            return(cardsData)
        }

        fun updateHandsUI(gameObject: JSONObject) {
            val max = gameObject.getString("max_cards").toInt()
            var playersInfoData = ""
            val playersArray = gameObject.getJSONArray("players")
            val nPlayers = playersArray.length()
            val handsArray = gameObject.getJSONArray("hands")

            when {
                handsArray.length() == 0 -> {
                    for (i in 0 until nPlayers) {
                        val iNickname = playersArray.getJSONObject(i).getString("nickname")
                        val iCards = playersArray.getJSONObject(i).getInt("n_cards")
                        val cardsData = showClosedHand(iCards, max)
                        if (nPlayers.mod(2) == 1 && i == nPlayers - 1) {
                            playersInfoData = playersInfoData.plus(makeFullTable(makeCell(
                                (formatNickname(iNickname, nickname, iCards > 0)).plus("<br>").plus(cardsData)
                            )))
                        } else {
                            playersInfoData = playersInfoData.plus(makeHalfTable(makeCell(
                                (formatNickname(iNickname, nickname, iCards > 0)).plus("<br>").plus(cardsData)
                            )))
                            if (i.mod(2) == 1) playersInfoData = playersInfoData.plus("<br>")
                        }
                    }
                }
                handsArray.length() == 1 -> {
                    for (i in 0 until playersArray.length()) {
                        val iNickname = playersArray.getJSONObject(i).getString("nickname")
                        val iCards = playersArray.getJSONObject(i).getInt("n_cards")
                        val cardsData =
                            if (iNickname == nickname) showOpenHand(handsArray.getJSONObject(0).getJSONArray("hand"), max)
                            else showClosedHand(iCards, max)
                        if (nPlayers.mod(2) == 1 && i == nPlayers - 1) {
                            playersInfoData = playersInfoData.plus(makeFullTable(makeCell(
                                (formatNickname(iNickname, nickname, iCards > 0)).plus("<br>").plus(cardsData)
                            )))
                        } else {
                            playersInfoData = playersInfoData.plus(makeHalfTable(makeCell(
                                (formatNickname(iNickname, nickname, iCards > 0)).plus("<br>").plus(cardsData)
                            )))
                            if (i.mod(2) == 1) playersInfoData = playersInfoData.plus("<br>")
                        }
                    }
                }
                else -> {
                    val activeNicknames = ArrayList<String>()
                    for (i in 0 until handsArray.length()) activeNicknames.add(handsArray.getJSONObject(i).getString("nickname"))

                    val historyArray = gameObject.getJSONArray("history")
                    val set = historyArray.getJSONObject(historyArray.length() - 3).getInt("action_id")
                    val setComplete = historyArray.getJSONObject(historyArray.length() - 2).getString("player") ==
                            historyArray.getJSONObject(historyArray.length() - 1).getString("player")

                    for (i in 0 until playersArray.length()) {
                        val iNickname = playersArray.getJSONObject(i).getString("nickname")
                        val active = activeNicknames.contains(iNickname)
                        val cardsData =
                            if (active) showOpenHand(handsArray.getJSONObject(activeNicknames.indexOf(iNickname)).getJSONArray("hand"), max, set, setComplete)
                            else showClosedHand(0, max)
                        if (nPlayers.mod(2) == 1 && i == nPlayers - 1) {
                            playersInfoData = playersInfoData.plus(makeFullTable(makeCell(
                                (formatNickname(iNickname, nickname, active)).plus("<br>").plus(cardsData)
                            )))
                        } else {
                            playersInfoData = playersInfoData.plus(makeHalfTable(makeCell(
                                (formatNickname(iNickname, nickname, active)).plus("<br>").plus(cardsData)
                            )))
                            if (i.mod(2) == 1) playersInfoData = playersInfoData.plus("<br>")
                        }
                    }
                }
            }
            playersInfo.loadDataWithBaseURL("file:///android_asset/", addCardTableStyle(playersInfoData), "text/html", "UTF-8", null)
        }

        fun generateRunningGameInfo(gameObject: JSONObject, gi: LinearLayout) {
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
                            makeRightCell(if (iAction <= 87) sets.sets[iAction] else getString(R.string.check_in_history))
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
                val active = iCards > 0
                playersInfoData = playersInfoData.plus(makeRow(
                    makeLeftCell(formatNickname(iNickname, nickname, active)),
                    makeRightCell(if (active) "\uD83C\uDFC6" else "")
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
                gameObject.getString("status") == GameStatuses.NOT_STARTED -> {
                    generatePreStartGameInfo(gameObject, gi)
                }
                gameObject.getString("status") == GameStatuses.RUNNING -> {
                    generateRunningGameInfo(gameObject, gi)
                }
                gameObject.getString("status") == GameStatuses.FINISHED -> {
                    generateFinishedGameInfo(gameObject, gi)
                }
            }
        }

        fun sendAction(actionId: Int) {
            queryEngine(
                R.id.activity_game,
                "$baseUrl/$gameUuid/play?player_uuid=$playerUuid&action_id=$actionId"
            ) { response ->
                val newMessage = response.body!!.string()
                val putUpdateOnHold = JSONObject(newMessage).isNull("cp_nickname")
                mHandler.post{
                    updateOnHold = putUpdateOnHold
                    message.setValue(newMessage)
                }
            }
        }

        val singleButtonParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        singleButtonParams.setMargins(0, adjustForDensity(6), 0, adjustForDensity(6))
        val leftButtonParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        leftButtonParams.setMargins(0, adjustForDensity(6), adjustForDensity(6), adjustForDensity(6))
        val rightButtonParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        rightButtonParams.setMargins(adjustForDensity(6), adjustForDensity(6), 0, adjustForDensity(6))
        val verticalButtonParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        verticalButtonParams.setMargins(0, 0, 0, adjustForDensity(6))

        val confirmButton = BlefButton(this@Game)
        confirmButton.text = getString(R.string.confirm)
        confirmButton.setOnClickListener {
            sendAction(sets.sets.indexOf(findViewById<PowerSpinnerView>(0).text))
            findViewById<PowerSpinnerView>(0).dismiss()
        }

        val checkButton = BlefButton(this@Game)
        checkButton.text = getString(R.string.check)
        checkButton.setOnClickListener {
            sendAction(88)
            findViewById<PowerSpinnerView>(0).dismiss()
        }

        val startButton = BlefButton(this@Game)
        startButton.tag = "start"
        startButton.text = getString(R.string.start_game)
        startButton.layoutParams = verticalButtonParams
        startButton.setOnClickListener{start()}

        val inviteButton = BlefButton(this@Game)
        inviteButton.tag = "inviteButton"
        inviteButton.text = getString(R.string.send_invite)
        inviteButton.layoutParams = verticalButtonParams
        inviteButton.setOnClickListener{
            val link = "${getString(R.string.join_me_for_a_game_of_blef)}: https://www.blef.app/join.html?game_uuid=$gameUuid"
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, link)
            startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
        }

        val publicPrivateButton = BlefButton(this@Game)
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
                setItems(sets.sets.subList(lastActionId + 1, 88))
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

        val typeNickname = EditText(this@Game)
        typeNickname.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, adjustForDensity(80), 1f)
            .apply{setMargins(0, adjustForDensity(6), adjustForDensity(6), adjustForDensity(6))}
        typeNickname.inputType = InputType.TYPE_CLASS_TEXT
        typeNickname.setText(sharedPref.getString("preferred_nickname", ""))
        typeNickname.hint = getString(R.string.nickname_hint)

        val generateNickname = MaterialButton(this@Game)
        generateNickname.text = """🎲"""
        generateNickname.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, adjustForDensity(80), 3f)
            .apply{setMargins(adjustForDensity(6), adjustForDensity(6), 0, adjustForDensity(6))}
        generateNickname.setOnClickListener {
            typeNickname.setText(generateNickname())
        }

        fun join() {
            val rawNickname = typeNickname.text.toString()
            val tryingNickname = rawNickname.replace(" ", "_")
            if(!"^[a-zA-Z]\\w*$".toRegex().matches(tryingNickname)) {
                val errorBar = Snackbar.make(findViewById(R.id.activity_game), getString(R.string.nickname_id_bad), 5000)
                errorBar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).maxLines = 5
                errorBar.show()
            } else {
                queryEngine(
                    R.id.activity_game,
                    "$baseUrl/$gameUuid/join?nickname=$tryingNickname"
                ) { response ->
                    val jsonBody = JSONObject(response.body!!.string())
                    playerUuid = jsonBody.getString("player_uuid")
                    nickname = tryingNickname
                    sharedPref.edit().putString("preferred_nickname", rawNickname).apply()
                    sharedPrefPlayerUuid.edit().putString(gameUuid, playerUuid).apply()
                    sharedPrefNickname.edit().putString(gameUuid, nickname).apply()
                }
            }
        }

        val confirmJoin = BlefButton(this@Game)
        confirmJoin.text = getString(R.string.join)
        confirmJoin.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply{
            setMargins(0, 0, 0, adjustForDensity(6))
        }
        confirmJoin.setOnClickListener {
            join()
        }

        val typeOrGenerate = LinearLayout(this@Game)
        typeOrGenerate.orientation = LinearLayout.HORIZONTAL
        typeOrGenerate.isBaselineAligned = false
        typeOrGenerate.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        typeOrGenerate.addView(typeNickname)
        typeOrGenerate.addView(generateNickname)

        val updateButton = BlefButton(this@Game)
        updateButton.tag = "update"
        updateButton.text = getString(R.string.go_to_current_round)
        updateButton.layoutParams = singleButtonParams
        updateButton.setOnClickListener{hardUpdateGame()}

        fun generateGameControls(gameObject: JSONObject) {
            val ll = findViewById<LinearLayout>(R.id.controlPanel)
            ll.removeAllViews()

            val history = gameObject.getJSONArray("history")
            when {
                updateOnHold -> {
                    ll.addView(updateButton)
                }
                gameObject.getString("cp_nickname") == nickname -> {
                    confirmOrCheck.removeAllViews()
                    when {
                        gameObject.getJSONArray("history").length() == 0 -> {
                            ll.addView(makeBetChooser(-1))
                            confirmOrCheck.addView(confirmButton)
                            confirmButton.layoutParams = singleButtonParams
                        }
                        history.getJSONObject(history.length() - 1).getInt("action_id") in 0..86 -> {
                            val lastAction = history.getJSONObject(history.length() - 1).getInt("action_id")
                            ll.addView(makeBetChooser(lastAction))
                            confirmOrCheck.addView(confirmButton)
                            confirmOrCheck.addView(checkButton)
                            confirmButton.layoutParams = leftButtonParams
                            checkButton.layoutParams = rightButtonParams
                        }
                        else -> {
                            confirmOrCheck.addView(checkButton)
                            checkButton.layoutParams = singleButtonParams
                        }
                    }
                    ll.addView(confirmOrCheck)
                }
                gameObject.getString("status") == GameStatuses.NOT_STARTED && nickname == gameObject.getString("admin_nickname") -> {
                    if (gameObject.getJSONArray("players").length() > 1) ll.addView(startButton)
                    if (gameObject.getString("public") == "false") {
                        publicPrivateButton.tag = "make_public"
                        publicPrivateButton.text = getString(R.string.make_public)
                        publicPrivateButton.setOnClickListener{makePublic()}
                    } else {
                        publicPrivateButton.tag = "make_private"
                        publicPrivateButton.text = getString(R.string.make_private)
                        publicPrivateButton.setOnClickListener{makePrivate()}
                    }
                    ll.addView(publicPrivateButton)
                    ll.addView(inviteButton)
                }
                gameObject.getString("status") == GameStatuses.NOT_STARTED && nickname != null -> {
                    ll.addView(inviteButton)
                }
                gameObject.getString("status") == GameStatuses.NOT_STARTED && nickname == null -> {
                    ll.addView(typeOrGenerate)
                    ll.addView(confirmJoin)
                }
            }
        }

        fun updateUI(message: String) {
            val gameObject = JSONObject(message)
            generateGameInfo(gameObject)
            generateGameControls(gameObject)

            val newHandsTrigger = gameObject.getInt("round_number").toString().plus("_").plus(gameObject.getJSONArray("hands").length().toString())
            if (newHandsTrigger != handsTrigger && gameObject.getString("status") == GameStatuses.RUNNING) {
                handsTrigger = newHandsTrigger
                updateHandsUI(gameObject)
            }

            if (gameObject.getString("status") == GameStatuses.FINISHED) {
                sharedPref.edit().putString("game_uuid", "").apply()
            }
            findViewById<TextView>(R.id.gamePlaceholder).visibility = View.GONE
        }

        message.observe(this@Game, { updateUI(message.value.toString()) })

        if (message.value != null) updateUI(message.value.toString())

        fixedRateTimer("update_game", false, 1000, 1000) {
            if (hasWindowFocus() && !gameFinished && !updateOnHold) updateGame()
        }
    }
}

