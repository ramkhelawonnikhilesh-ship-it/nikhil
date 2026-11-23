package mu.ac.uom.s0000000.tikafe

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class Screen(val title: Int) {
    HOME(R.string.home), MENU(R.string.menu), CUSTOM(R.string.customise), BASKET(R.string.basket),
    ORDERS(R.string.orders), DETAILS(R.string.details), BRANCHES(R.string.branches), SETTINGS(R.string.settings), STAFF(R.string.staff)
}

class ShopState : ViewModel() {
    var screen by mutableStateOf(Screen.HOME)
    var detailsBack by mutableStateOf(Screen.ORDERS)
    var product by mutableStateOf<Product?>(null)
    var orderId by mutableLongStateOf(0L)
    var draft by mutableStateOf(emptyList<Choice>())
    var bakery by mutableStateOf(false)
    var lines by mutableStateOf(emptyList<Line>())
    var branchId by mutableStateOf("")
    var pickup by mutableStateOf<Long?>(null)
    var redeem by mutableStateOf(false)
    var revision by mutableIntStateOf(0)
    var message by mutableStateOf<String?>(null)

    fun customise(product: Product) {
        this.product = product
        draft = listOfNotNull(sizes.first().takeIf { product.sizes }, milks.first().takeIf { product.milk }, sugars[2])
        screen = Screen.CUSTOM
    }
    fun option(group: String, choice: Choice?) {
        draft = (draft.filterNot { it.group == group } + listOfNotNull(choice))
            .sortedBy { listOf("size","milk","shot","sugar").indexOf(it.group) }
    }
    fun quantity(line: Line, count: Int) {
        val index = lines.indexOfFirst { it.product.id == line.product.id && it.options == line.options }
        lines = lines.toMutableList().apply {
            if (index >= 0) {
                if (count <= 0) removeAt(index) else set(index,line.copy(qty = count))
            } else if (count > 0) add(line.copy(qty = count))
        }
    }
    fun add(line: Line) {
        val existing = lines.firstOrNull { it.product.id == line.product.id && it.options == line.options }
        quantity(line,(existing?.qty ?: 0) + line.qty)
    }
    fun choose(id: String) {
        if (branchId != id) pickup = null
        branchId = id
        revision++
    }
    fun details(id: Long, from: Screen = Screen.ORDERS) {
        orderId = id
        detailsBack = from
        screen = Screen.DETAILS
    }
    fun back(staff: Boolean) {
        screen = when (screen) {
            Screen.CUSTOM, Screen.BASKET -> Screen.MENU
            Screen.DETAILS -> detailsBack
            else -> if (staff) Screen.STAFF else Screen.HOME
        }
    }
}

class ShopActivity : AppCompatActivity() {
    private val db by lazy { ShopDb(this) }
    private val state by lazy { ViewModelProvider(this)[ShopState::class.java] }
    private val session by lazy { Session(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stored = session.user
        val id = intent.getLongExtra("user",stored).takeIf { it == stored } ?: stored
        if (id == 0L || db.user(id) == null) { openSignIn(intent.getLongExtra("order",0)); return }
        Notices.channels(this)
        enableEdgeToEdge()
        if (state.branchId.isEmpty()) {
            val user = requireNotNull(db.user(id))
            state.branchId = user.favourite
            state.screen = if (user.staff == null) Screen.HOME else Screen.STAFF
        }
        if (savedInstanceState == null) openLink(intent)
        setContent { Shop(id) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openLink(intent)
    }
    override fun onResume() {
        super.onResume()
        if (session.user != 0L) {
            state.revision++
            db.user(session.user)?.takeIf { it.staff == null }?.let { user ->
                db.orders(user).forEach { Arrival.register(this,it) }
            }
        }
    }
    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }
    private fun openLink(intent: Intent) {
        val id = intent.getLongExtra("order",0)
        val user = db.user(session.user) ?: return
        if (id != 0L && db.orders(user).any { it.id == id }) {
            state.details(id, if (user.staff == null) Screen.ORDERS else Screen.STAFF)
        }
    }
    private fun signOut() = openSignIn()
    private fun openSignIn(order: Long = 0L) {
        session.user = 0
        startActivity(Intent(this,SignInActivity::class.java).putExtra("order",order)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Shop(id: Long) {
        val screen = state.screen
        val revision = state.revision
        val user = remember(id,revision) { requireNotNull(db.user(id)) }
        val arrivalRevision by Arrival.arrivals.collectAsState()
        val orders = remember(user,screen,revision,arrivalRevision) { db.orders(user) }
        val staff = user.staff != null
        val root = if (staff) Screen.STAFF else Screen.HOME
        val selectedBranch = branch(state.branchId)
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.message) {
            state.message?.let {
                snackbar.showSnackbar(it)
                state.message = null
            }
        }
        BackHandler(screen != root) { state.back(staff) }
        MaterialTheme(colorScheme = lightColorScheme(
            primary = Color(0xFF295740), onPrimary = Color.White,
            primaryContainer = Color(0xFFD5E8D4), onPrimaryContainer = Color(0xFF193526),
            secondaryContainer = Color(0xFFF1DFCA), background = Color(0xFFFFF9F0),
            surface = Color(0xFFFFF9F0), surfaceVariant = Color(0xFFF0E5D8)
        )) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(screen.title),Modifier.semantics { heading() }) },
                        navigationIcon = {
                            if (screen != root) TextButton(onClick = { state.back(staff) }) {
                                Text(stringResource(R.string.back))
                            }
                        },
                        actions = {
                            if (screen == Screen.STAFF) TextButton(onClick = ::signOut) {
                                Text(stringResource(R.string.signout))
                            }
                        })
                },
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (screen == Screen.MENU) {
                        Surface(shadowElevation = 4.dp) {
                            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
                                Button(onClick = { state.screen = Screen.BASKET },Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.basket_bar,state.lines.sumOf { it.qty },money(bill(state.lines,false).total)))
                                }
                            }
                        }
                    }
                }
            ) { insets ->
                Box(Modifier.fillMaxSize().padding(insets)) {
                    when (screen) {
                        Screen.HOME -> Page {
                            Text(stringResource(R.string.welcome,user.name),style = MaterialTheme.typography.headlineMedium)
                            ElevatedCard(Modifier.fillMaxWidth()) { ProductImage(products[2]) }
                            if (BuildConfig.DATABASE_VERSION >= 2) Text(stamps(user.stamps))
                            Caption(R.string.favourite,user.favourite.let(::branch).name)
                            Caption(R.string.last_order, orders.firstOrNull()?.let { stringResource(R.string.order_number,it.id) }
                                ?: stringResource(R.string.no_orders))
                            listOf(Screen.MENU,Screen.ORDERS,Screen.BRANCHES,Screen.SETTINGS).forEach {
                                Button(onClick = { state.screen = it },Modifier.fillMaxWidth()) { Text(stringResource(it.title)) }
                            }
                        }
                        Screen.MENU -> {
                            val stock = remember(screen,revision,selectedBranch) { db.stock(selectedBranch.id) }
                            Menu(state,selectedBranch,stock)
                        }
                        Screen.CUSTOM -> state.product?.let { Customise(state,it) }
                        Screen.BASKET -> Basket(state,user,selectedBranch) {
                            val slot = state.pickup ?: return@Basket
                            val use = state.redeem && user.stamps >= 10 && state.lines.any { it.product.drink }
                            val order = try {
                                db.place(user.id,selectedBranch,slot,state.lines,use)
                            } catch (_: IllegalArgumentException) {
                                state.pickup = null
                                state.revision++
                                state.message = getString(R.string.checkout_error)
                                return@Basket
                            } catch (_: android.database.sqlite.SQLiteException) {
                                state.message = getString(R.string.checkout_error)
                                return@Basket
                            }
                            state.lines = emptyList()
                            state.pickup = null
                            state.redeem = false
                            state.revision++
                            state.details(order)
                            db.orders(user).firstOrNull { it.id == order }?.let { Arrival.register(this@ShopActivity,it) }
                        }
                        Screen.ORDERS -> Orders(state,orders,remember(revision) { db.summary(user.id) })
                        Screen.DETAILS -> orders.firstOrNull { it.id == state.orderId }?.let { order ->
                            Details(order,remember(order.id) { db.lines(order.id) },!staff,
                                cancel = {
                                    if (db.cancel(user.id,order.id)) Arrival.remove(this@ShopActivity,order.id)
                                    state.revision++
                                },
                                again = { reorder(order) },
                                permission = { PermissionSettings(includeArrival = true) })
                        }
                        Screen.BRANCHES -> BranchMap(selectedBranch, onChoose = {
                            state.choose(it.id)
                            db.favourite(user.id,it.id)
                        })
                        Screen.SETTINGS -> Page {
                            Caption(R.string.language,"")
                            val selected = AppCompatDelegate.getApplicationLocales().get(0)?.language ?: "en"
                            listOf("en" to R.string.english,"fr" to R.string.french,"mfe" to R.string.kreol).forEach { (tag,label) ->
                                FilterChip(selected = selected == tag,onClick = {
                                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                                },label = { Text(stringResource(label)) })
                            }
                            Toggle(R.string.offers,user.offers) {
                                db.offers(user.id,it)
                                state.revision++
                            }
                            BranchPicker(branch(user.favourite), R.string.favourite) {
                                db.favourite(user.id,it.id)
                                state.choose(it.id)
                            }
                            PermissionSettings(includeArrival = true)
                            OutlinedButton(onClick = ::signOut) { Text(stringResource(R.string.signout)) }
                        }
                        Screen.STAFF -> {
                            val assigned = branch(requireNotNull(user.staff))
                            Staff(state,orders,remember(revision) { db.stock(assigned.id) },
                                stock = { product, qty -> db.setStock(user,product,qty); state.revision++ },
                                offer = { if (db.offerAudience(assigned.id)) Notices.offer(this@ShopActivity,assigned) },
                                advance = { order ->
                                    db.advance(user,order.id)?.let {
                                        if (it.status == Status.READY) Notices.ready(this@ShopActivity,it)
                                        if (it.status == Status.COLLECTED) Arrival.remove(this@ShopActivity,it.id)
                                    }
                                    state.revision++
                                })
                        }
                    }
                }
            }
        }
    }

    private fun reorder(order: Order) {
        val stock = db.stock(state.branchId)
        val skipped = mutableListOf<String>()
        db.lines(order.id).forEach { line ->
            val used = state.lines.filter { it.product.id == line.product.id }.sumOf { it.qty }
            val remaining = stock[line.product.id]?.minus(used)?.coerceAtLeast(0) ?: line.qty
            val count = minOf(line.qty,remaining)
            if (count > 0) state.add(line.copy(qty = count))
            if (count < line.qty) skipped += getString(line.product.name)
        }
        state.screen = Screen.BASKET
        if (skipped.isNotEmpty()) state.message = getString(R.string.skipped,skipped.joinToString())
    }

    @Composable
    private fun PermissionSettings(includeArrival: Boolean) {
        var reason by remember { mutableStateOf<Int?>(null) }
        val revision = state.revision
        val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { state.revision++ }
        val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { state.revision++ }
        val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            state.revision++
            if (granted(Manifest.permission.ACCESS_FINE_LOCATION) && Build.VERSION.SDK_INT >= 29) reason = R.string.arrival_reason
        }
        val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { state.revision++ }
        val notificationGranted = remember(revision) { granted(Manifest.permission.POST_NOTIFICATIONS) }
        if (Build.VERSION.SDK_INT >= 33 && !notificationGranted) {
            OutlinedButton(onClick = { reason = R.string.notification_reason }) { Text(stringResource(R.string.notifications)) }
        }
        if (includeArrival && !Arrival.allowed(this)) {
            OutlinedButton(onClick = { reason = R.string.arrival_reason }) { Text(stringResource(R.string.arrival_permission)) }
        }
        reason?.let { message ->
            AlertDialog(onDismissRequest = { reason = null },text = { Text(stringResource(message)) },
                confirmButton = {
                    TextButton(onClick = {
                        reason = null
                        when {
                            message == R.string.notification_reason -> notification.launch(Manifest.permission.POST_NOTIFICATIONS)
                            !granted(Manifest.permission.ACCESS_FINE_LOCATION) -> foreground.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
                            Build.VERSION.SDK_INT == 29 -> background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            else -> settings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                        }
                    }) { Text(stringResource(R.string.choose)) }
                }, dismissButton = {
                    TextButton(onClick = { reason = null }) { Text(stringResource(R.string.back)) }
                })
        }
    }
}

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),content = content)
}

@Composable
fun Caption(label: Int, value: String) {
    Column {
        Text(stringResource(label),style = MaterialTheme.typography.titleMedium,modifier = Modifier.semantics { heading() })
        if (value.isNotEmpty()) Text(value)
    }
}

@Composable
fun Toggle(label: Int, checked: Boolean, update: (Boolean) -> Unit) {
    val description = stringResource(label)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label),Modifier.weight(1f))
        Switch(checked = checked,onCheckedChange = update,modifier = Modifier.semantics {
            contentDescription = description
        })
    }
}
