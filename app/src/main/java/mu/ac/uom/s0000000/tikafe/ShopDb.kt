package mu.ac.uom.s0000000.tikafe

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import java.util.Locale
import kotlin.random.Random

data class User(val id: Long, val name: String, val favourite: String, val staff: String?, val offers: Boolean, val stamps: Int)
data class Order(val id: Long, val user: Long, val customer: String, val branch: String, val pickup: Long,
    val created: Long, val code: String, val status: Status, val arriving: Boolean, val bill: Bill)

class ShopDb(context: Context) : SQLiteOpenHelper(context.applicationContext, "tikafe.db", null, BuildConfig.DATABASE_VERSION) {
    override fun onConfigure(db: SQLiteDatabase) = db.setForeignKeyConstraintsEnabled(true)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE users(id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE,
            password TEXT NOT NULL, phone TEXT NOT NULL, favourite TEXT NOT NULL, role TEXT NOT NULL,
            staff_branch TEXT, offers INTEGER NOT NULL DEFAULT 1)""")
        db.execSQL("CREATE TABLE products(id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, price INTEGER NOT NULL, daily_bake INTEGER NOT NULL)")
        db.execSQL("""CREATE TABLE orders(id INTEGER PRIMARY KEY, customer INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            branch TEXT NOT NULL, pickup INTEGER NOT NULL, created INTEGER NOT NULL, code TEXT NOT NULL,
            status TEXT NOT NULL, subtotal INTEGER NOT NULL, discount INTEGER NOT NULL, total INTEGER NOT NULL,
            arriving INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE order_lines(id INTEGER PRIMARY KEY, order_id INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
            product TEXT NOT NULL REFERENCES products(id) ON DELETE CASCADE, quantity INTEGER NOT NULL CHECK(quantity > 0), unit INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE line_options(line_id INTEGER NOT NULL REFERENCES order_lines(id) ON DELETE CASCADE,
            kind TEXT NOT NULL, option_key TEXT NOT NULL, PRIMARY KEY(line_id,kind))""")
        db.execSQL("""CREATE TABLE stock(day TEXT NOT NULL, branch TEXT NOT NULL,
            product TEXT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
            remaining INTEGER NOT NULL CHECK(remaining >= 0), PRIMARY KEY(day,branch,product))""")
        products.forEach { db.insertOrThrow("products", null, values("id" to it.id, "name" to it.id, "price" to it.price, "daily_bake" to it.bake)) }
        val demo = addUser(db,"Demo Customer","demo@tikafe.mu","51234567","Demo2026",null)
        addUser(db,"Rose Hill Staff","staff.rh@tikafe.mu","52345678","Staff2026","RH")
        val now = muNow()
        writeOrder(db, demo, "RH", now.minusDays(2).toInstant().toEpochMilli(), now.minusDays(2).toInstant().toEpochMilli(),
            listOf(Line(products[2], listOf(sizes[1], milks[3], sugars[1]))), false, Status.COLLECTED)
        writeOrder(db, demo, "RH", now.minusDays(1).toInstant().toEpochMilli(), now.minusDays(1).toInstant().toEpochMilli(),
            listOf(Line(products[1], listOf(sizes[0], milks[0], sugars[2]), 2)), false, Status.COLLECTED)
        if (BuildConfig.DATABASE_VERSION >= 2) addLoyalty(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2 && newVersion >= 2) addLoyalty(db)
    }

    private fun addLoyalty(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN stamps INTEGER NOT NULL DEFAULT 0 CHECK(stamps >= 0)")
        db.update("users", values("stamps" to 8), "email = ?", arrayOf("demo@tikafe.mu"))
    }

    private fun addUser(db: SQLiteDatabase, name: String, email: String, phone: String, password: String, staff: String?): Long =
        db.insertOrThrow("users", null, values("name" to name, "email" to email, "phone" to phone,
            "password" to Passwords.hash(password), "favourite" to "RH",
            "role" to if (staff == null) "customer" else "staff", "staff_branch" to staff))

    fun register(name: String, email: String, phone: String, password: String) =
        addUser(writableDatabase, name, email.lowercase(Locale.ROOT), phone, password, null)

    fun authenticate(email: String, password: String): Long? =
        readableDatabase.rawQuery("SELECT id,password FROM users WHERE email = ?", arrayOf(email.lowercase(Locale.ROOT))).use {
            if (it.moveToFirst() && Passwords.matches(password,it.getString(1))) it.getLong(0) else null
        }

    fun user(id: Long): User? = user(readableDatabase, id)
    private fun user(db: SQLiteDatabase, id: Long): User? =
        db.rawQuery("SELECT * FROM users WHERE id = ?", arrayOf(id.toString())).use {
            if (!it.moveToFirst()) return null
            User(id, it.str("name"), it.str("favourite"), if (it.str("role") == "staff") it.str("staff_branch") else null,
                it.num("offers") == 1, if (BuildConfig.DATABASE_VERSION >= 2) it.num("stamps") else 0)
        }

    fun favourite(user: Long, branch: String) = writableDatabase.update("users", values("favourite" to branch), "id = ?", arrayOf(user.toString()))
    fun offers(user: Long, enabled: Boolean) = writableDatabase.update("users", values("offers" to if (enabled) 1 else 0), "id = ?", arrayOf(user.toString()))
    fun offerAudience(branch: String) = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM users WHERE role = 'customer' AND favourite = ? AND offers = 1", arrayOf(branch)
    ).use { it.moveToFirst() && it.getInt(0) > 0 }

    fun stock(branch: String, day: String = muNow().toLocalDate().toString()): Map<String,Int> = stock(readableDatabase, branch, day)
    private fun stock(db: SQLiteDatabase, branch: String, day: String): Map<String,Int> =
        db.rawQuery("""SELECT p.id,COALESCE(s.remaining,p.daily_bake) FROM products p
            LEFT JOIN stock s ON s.product = p.id AND s.day = ? AND s.branch = ? WHERE p.daily_bake > 0""", arrayOf(day,branch))
            .use { cursor -> buildMap { while (cursor.moveToNext()) put(cursor.getString(0),cursor.getInt(1)) } }

    fun setStock(staff: User, product: Product, amount: Int) {
        val assigned = requireNotNull(staff.staff)
        require(product.bake > 0 && amount >= 0)
        writeStock(writableDatabase, assigned, product.id, muNow().toLocalDate().toString(), amount)
    }
    private fun writeStock(db: SQLiteDatabase, branch: String, product: String, day: String, amount: Int) {
        db.insertWithOnConflict("stock", null, values("day" to day, "branch" to branch, "product" to product,
            "remaining" to amount), SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
    }

    fun place(user: Long, branch: Branch, pickup: Long, lines: List<Line>, redeem: Boolean): Long = transaction { db ->
        val now = muNow()
        require(branch.validSlot(pickup, now) && lines.isNotEmpty())
        val customer = requireNotNull(user(db, user))
        require(customer.staff == null)
        require(!redeem || (customer.stamps >= 10 && lines.any { it.product.drink }))
        val day = Instant.ofEpochMilli(pickup).atZone(muZone).toLocalDate().toString()
        val available = stock(db, branch.id, day)
        lines.filterNot { it.product.drink }.groupBy { it.product.id }.forEach { (id, group) ->
            val left = available.getValue(id) - group.sumOf { it.qty }
            require(left >= 0)
            writeStock(db, branch.id, id, day, left)
        }
        val id = writeOrder(db,user,branch.id,pickup,now.toInstant().toEpochMilli(),lines,redeem,Status.RECEIVED)
        if (redeem) adjustStamps(db, user, -10)
        id
    }

    private fun writeOrder(db: SQLiteDatabase, user: Long, branch: String, pickup: Long, created: Long,
                           lines: List<Line>, redeem: Boolean, status: Status): Long {
        val bill = bill(lines,redeem)
        val id = db.insertOrThrow("orders",null,values("customer" to user,"branch" to branch,"pickup" to pickup,
            "created" to created,"code" to Random.nextInt(1000,10000).toString(),"status" to status.name,
            "subtotal" to bill.subtotal,"discount" to bill.discount,"total" to bill.total))
        lines.forEach { line ->
            val lineId = db.insertOrThrow("order_lines",null,values("order_id" to id,"product" to line.product.id,
                "quantity" to line.qty,"unit" to line.unit))
            line.options.forEach {
                db.insertOrThrow("line_options",null,values("line_id" to lineId,"kind" to it.group,"option_key" to it.key))
            }
        }
        return id
    }

    private fun adjustStamps(db: SQLiteDatabase, customer: Long, change: Int) {
        if (BuildConfig.DATABASE_VERSION >= 2) db.execSQL("UPDATE users SET stamps = stamps + ? WHERE id = ?", arrayOf(change,customer))
    }

    private fun queryOrders(db: SQLiteDatabase, where: String, args: Array<String>) =
        db.rawQuery("SELECT o.*,u.name AS customer_name FROM orders o JOIN users u ON u.id = o.customer WHERE $where ORDER BY o.created DESC,o.id DESC",args)
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.asOrder()) } }

    fun orders(user: User): List<Order> = if (user.staff == null)
        queryOrders(readableDatabase, "o.customer = ?", arrayOf(user.id.toString()))
    else queryOrders(readableDatabase, "o.branch = ?", arrayOf(user.staff))

    private fun order(db: SQLiteDatabase, id: Long) = queryOrders(db,"o.id = ?",arrayOf(id.toString())).firstOrNull()

    fun lines(id: Long): List<Line> = lines(readableDatabase,id)
    private fun lines(db: SQLiteDatabase, id: Long): List<Line> =
        db.rawQuery("""SELECT l.id,l.product,l.quantity,op.option_key FROM order_lines l LEFT JOIN line_options op
            ON l.id = op.line_id WHERE l.order_id = ? ORDER BY l.id,CASE op.kind WHEN 'size' THEN 1 WHEN 'milk' THEN 2 WHEN 'shot' THEN 3 ELSE 4 END""",
            arrayOf(id.toString())).use { cursor ->
                val result = linkedMapOf<Long, Line>()
                while (cursor.moveToNext()) {
                    val key = cursor.getLong(0)
                    val line = result[key] ?: Line(productsById.getValue(cursor.getString(1)), qty = cursor.getInt(2))
                    result[key] = line.copy(options = line.options + listOfNotNull(cursor.getString(3)?.let(choices::get)))
                }
                result.values.toList()
            }

    fun summary(user: Long): Pair<Int, Product?> {
        val monthStart = muNow().withDayOfMonth(1).toLocalDate().atStartOfDay(muZone).toInstant().toEpochMilli()
        val sum = readableDatabase.rawQuery("""SELECT COALESCE(SUM(total),0) FROM orders
            WHERE customer = ? AND created >= ? AND status = 'COLLECTED'""", arrayOf(user.toString(),monthStart.toString()))
            .use { it.moveToFirst(); it.getInt(0) }
        val popular = readableDatabase.rawQuery("""SELECT l.product,COUNT(*) AS line_count,SUM(l.quantity) AS units
            FROM order_lines l JOIN orders o ON o.id = l.order_id WHERE o.customer = ? AND o.status != 'CANCELLED'
            GROUP BY l.product ORDER BY units DESC,line_count DESC,l.product LIMIT 1""", arrayOf(user.toString()))
            .use { if (it.moveToFirst()) productsById[it.getString(0)] else null }
        return sum to popular
    }

    fun advance(staff: User, id: Long): Order? = transaction { db ->
        val current = order(db,id) ?: return@transaction null
        if (staff.staff != current.branch) return@transaction null
        val next = when (current.status) {
            Status.RECEIVED -> Status.PREPARING
            Status.PREPARING -> Status.READY
            Status.READY -> Status.COLLECTED
            else -> return@transaction null
        }
        db.update("orders",values("status" to next.name),"id = ?",arrayOf(id.toString()))
        if (next == Status.COLLECTED) adjustStamps(db,current.user,lines(db,id).filter { it.product.drink }.sumOf { it.qty })
        current.copy(status = next)
    }

    fun cancel(owner: Long, id: Long): Boolean = transaction { db ->
        val current = order(db,id) ?: return@transaction false
        if (current.user != owner || current.status != Status.RECEIVED) return@transaction false
        db.update("orders",values("status" to Status.CANCELLED.name),"id = ?",arrayOf(id.toString()))
        if (current.bill.discount > 0) adjustStamps(db,owner,10)
        val day = Instant.ofEpochMilli(current.pickup).atZone(muZone).toLocalDate().toString()
        val stock = stock(db,current.branch,day)
        lines(db,id).filterNot { it.product.drink }.groupBy { it.product.id }.forEach { (product,group) ->
            writeStock(db,current.branch,product,day,stock.getValue(product) + group.sumOf { it.qty })
        }
        true
    }

    fun markArriving(id: Long): Boolean = writableDatabase.update("orders",values("arriving" to 1),
        "id = ? AND arriving = 0 AND status IN ('RECEIVED','PREPARING','READY')",arrayOf(id.toString())) == 1

    private fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    private fun Cursor.str(key: String): String = getString(getColumnIndexOrThrow(key))
    private fun Cursor.num(key: String): Int = getInt(getColumnIndexOrThrow(key))
    private fun Cursor.long(key: String): Long = getLong(getColumnIndexOrThrow(key))
    private fun Cursor.asOrder() = Order(long("id"),long("customer"),str("customer_name"),str("branch"),long("pickup"),long("created"),
        str("code"),Status.valueOf(str("status")),num("arriving") == 1,Bill(num("subtotal"),num("discount")))

    private fun values(vararg pairs: Pair<String,Any?>) = ContentValues().apply {
        for ((key,value) in pairs) when (value) {
            null -> putNull(key)
            is String -> put(key,value)
            is Int -> put(key,value)
            is Long -> put(key,value)
            else -> error("Unsupported value")
        }
    }
}
