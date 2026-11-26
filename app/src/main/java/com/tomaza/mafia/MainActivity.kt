package com.tomaza.mafia

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random

data class DeviceProfile(
    val androidId: String, val imei: String,
    val brand: String, val model: String, val fingerprint: String, val mac: String
)

object ProfileFactory {
    private val devices = listOf(
        Triple("samsung", "SM-S911B", "samsung/dm1qxx/dm1q:14/UP1A.231005.007/S911BXXU2:user/release-keys"),
        Triple("google", "Pixel 7", "google/panther/panther:14/UP1A.231005.007/10754064:user/release-keys"),
        Triple("Xiaomi", "2201123G", "Xiaomi/ziyi/ziyi:13/TKQ1.220829.002/V14:user/release-keys")
    )

    fun create(seed: String): DeviceProfile {
        val r = Random(seed.hashCode().toLong())
        val (brand, model, fp) = devices[r.nextInt(devices.size)]
        return DeviceProfile(
            androidId = (1..16).joinToString("") { "%x".format(r.nextInt(16)) },
            imei = imei(r), brand = brand, model = model,
            fingerprint = fp,
            mac = (1..6).joinToString(":") { "%02x".format(r.nextInt(256)) }
        )
    }

    private fun imei(r: Random): String {
        val d = IntArray(15) { r.nextInt(10) }
        var sum = 0
        for (i in 0 until 14) {
            var x = d[i]
            if (i % 2 == 1) {
                x *= 2
                if (x > 9) x -= 9
            }
            sum += x
        }
        d[14] = (10 - sum % 10) % 10
        return d.joinToString("")
    }
}

data class CloneEntry(val id: Int, val pkg: String, val label: String)

object CloneStore {
    private fun prefs(c: Context) =
        c.getSharedPreferences("clones", Context.MODE_PRIVATE)

    fun all(c: Context): MutableList<CloneEntry> {
        val arr = JSONArray(prefs(c).getString("list", "[]"))
        return MutableList(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            CloneEntry(
                o.getInt("id"),
                o.getString("pkg"),
                o.getString("label")
            )
        }
    }

    private fun save(c: Context, l: List<CloneEntry>) {
        val arr = JSONArray()
        l.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("pkg", it.pkg)
                    .put("label", it.label)
            )
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }

    fun add(c: Context, pkg: String, label: String) {
        val l = all(c)
        val next = prefs(c).getInt("next", 1)
        prefs(c).edit().putInt("next", next + 1).apply()
        l.add(CloneEntry(next, pkg, label))
        save(c, l)
    }

    fun remove(c: Context, id: Int) {
        save(c, all(c).filter { it.id != id })
    }
}

interface CloneEngine {
    fun launch(activity: Activity, entry: CloneEntry, profile: DeviceProfile)
}

object Engines {
    var current: CloneEngine = BlackBoxEngine()
}

class MainActivity : Activity() {
    private lateinit var listView: ListView
    private lateinit var empty: TextView
    private var items = mutableListOf<CloneEntry>()

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    private fun row(icon: Drawable?, title: String, sub: String): View {
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }

        h.addView(
            ImageView(this).apply {
                setImageDrawable(icon)
            },
            LinearLayout.LayoutParams(dp(48), dp(48))
        )

        val t = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }

        t.addView(TextView(this).apply {
            text = title
            textSize = 16f
        })

        t.addView(TextView(this).apply {
            text = sub
            textSize = 12f
            setTextColor(Color.GRAY)
        })

        h.addView(t)
        return h
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        col.addView(TextView(this).apply {
            text = "المستنسخ"
            textSize = 22f
            setPadding(dp(20), dp(40), dp(20), dp(16))
        })

        listView = ListView(this)

        empty = TextView(this).apply {
            text = "مفيش تطبيقات مستنسخة\nاضغط + لإضافة تطبيق"
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            setPadding(0, dp(80), 0, 0)
        }

        col.addView(empty)
        col.addView(listView, LinearLayout.LayoutParams(-1, -1))
        root.addView(col, FrameLayout.LayoutParams(-1, -1))

        val fab = TextView(this).apply {
            text = "+"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            elevation = dp(6).toFloat()

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF6200EE.toInt())
            }

            setOnClickListener {
                showPicker()
            }
        }

        root.addView(
            fab,
            FrameLayout.LayoutParams(
                dp(64),
                dp(64),
                Gravity.BOTTOM or Gravity.END
            ).apply {
                setMargins(0, 0, dp(24), dp(32))
            }
        )

        setContentView(root)

        listView.setOnItemClickListener { _, _, pos, _ ->
            val e = items[pos]
            Engines.current.launch(
                this,
                e,
                ProfileFactory.create("clone-${e.id}")
            )
        }

        listView.setOnItemLongClickListener { _, _, pos, _ ->
            val e = items[pos]

            AlertDialog.Builder(this)
                .setTitle("حذف ${e.label}؟")
                .setPositiveButton("حذف") { _, _ ->
                    CloneStore.remove(this, e.id)
                    refresh()
                }
                .setNegativeButton("إلغاء", null)
                .show()

            true
        }

        refresh()
    }

    private fun refresh() {
        items = CloneStore.all(this)

        empty.visibility =
            if (items.isEmpty()) View.VISIBLE else View.GONE

        listView.adapter = object : BaseAdapter() {
            override fun getCount() = items.size

            override fun getItem(p: Int) = items[p]

            override fun getItemId(p: Int) =
                items[p].id.toLong()

            override fun getView(
                p: Int,
                v: View?,
                parent: ViewGroup
            ): View {
                val e = items[p]

                val icon = try {
                    packageManager.getApplicationIcon(e.pkg)
                } catch (_: Exception) {
                    null
                }

                return row(icon, e.label, "نسخة رقم ${e.id}")
            }
        }
    }

    private fun showPicker() {
        val pm = packageManager

        val q = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)

        val apps = pm.queryIntentActivities(q, 0)
            .map {
                it.activityInfo.packageName to
                it.loadLabel(pm).toString()
            }
            .distinctBy { it.first }
            .filter { it.first != packageName }
            .sortedBy { it.second.toLowerCase() }

        val lv = ListView(this)

        val dialog = AlertDialog.Builder(this)
            .setTitle("اختر تطبيق")
            .setView(lv)
            .create()

        lv.adapter = object : BaseAdapter() {
            override fun getCount() = apps.size

            override fun getItem(p: Int) = apps[p]

            override fun getItemId(p: Int) = p.toLong()

            override fun getView(
                p: Int,
                v: View?,
                parent: ViewGroup
            ): View {
                return row(
                    pm.getApplicationIcon(apps[p].first),
                    apps[p].second,
                    apps[p].first
                )
            }
        }

        lv.setOnItemClickListener { _, _, pos, _ ->
            CloneStore.add(
                this,
                apps[pos].first,
                apps[pos].second
            )

            dialog.dismiss()
            refresh()
        }

        dialog.show()
    }
}