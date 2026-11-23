package mu.ac.uom.s0000000.tikafe

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.util.Base64
import android.util.Patterns
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.ac.uom.s0000000.tikafe.databinding.ActivityRegisterBinding
import mu.ac.uom.s0000000.tikafe.databinding.ActivitySignInBinding
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Passwords {
    fun hash(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return encode(salt) + ":" + encode(derive(password,salt))
    }
    fun matches(password: String, stored: String): Boolean {
        val (salt, hash) = stored.split(':')
        return MessageDigest.isEqual(derive(password, decode(salt)), decode(hash))
    }
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes,Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value,Base64.NO_WRAP)
    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(),salt,120_000,256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

class Session(context: Context) {
    private val preferences = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    var user: Long
        get() = preferences.getLong("user",0)
        set(id) { preferences.edit().putLong("user",id).apply() }
}

abstract class AccountActivity : AppCompatActivity() {
    protected val db get() = ShopDb(this)
    protected fun show(root: android.view.View) {
        enableEdgeToEdge()
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.updatePadding(left = safe.left, top = safe.top, right = safe.right, bottom = safe.bottom)
            insets
        }
    }
    protected fun openShop(id: Long) {
        Session(this).user = id
        startActivity(Intent(this, ShopActivity::class.java).putExtra("user", id)
            .putExtra("order", intent.getLongExtra("order", 0L))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }
}

class SignInActivity : AccountActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notices.channels(this)
        Session(this).user.takeIf { it > 0 }?.let { openShop(it); return }
        val binding = ActivitySignInBinding.inflate(layoutInflater)
        show(binding.root)
        binding.alternate.setOnClickListener { startActivity(Intent(this,RegisterActivity::class.java)) }
        binding.submit.setOnClickListener {
            val email = binding.email.text.toString().trim()
            val password = binding.password.text.toString()
            binding.submit.isEnabled = false
            lifecycleScope.launch {
                val id = withContext(Dispatchers.IO) { db.use { it.authenticate(email,password) } }
                binding.submit.isEnabled = true
                if (id == null) binding.password.error = getString(R.string.invalid_login) else openShop(id)
            }
        }
    }
}

class RegisterActivity : AccountActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityRegisterBinding.inflate(layoutInflater)
        show(binding.root)
        binding.alternate.setOnClickListener { finish() }
        binding.submit.setOnClickListener {
            val name = binding.name.text.toString().trim()
            val email = binding.email.text.toString().trim()
            val phone = binding.phone.text.toString().trim()
            val password = binding.password.text.toString()
            val confirm = binding.confirm.text.toString()
            val invalid = listOf(
                Triple(name.isBlank(), binding.name, R.string.required),
                Triple(!Patterns.EMAIL_ADDRESS.matcher(email).matches(), binding.email,R.string.bad_email),
                Triple(!Regex("5[0-9]{7}").matches(phone), binding.phone,R.string.bad_phone),
                Triple(password.length < 8 || !password.any { it.isLetter() } || !password.any { it.isDigit() },
                    binding.password,R.string.bad_password),
                Triple(confirm.isEmpty() || confirm != password, binding.confirm,R.string.bad_match)
            )
            invalid.forEach { (bad, field, message) -> field.error = if (bad) getString(message) else null }
            invalid.firstOrNull { it.first }?.let { it.second.requestFocus(); return@setOnClickListener }
            binding.submit.isEnabled = false
            lifecycleScope.launch {
                val id = withContext(Dispatchers.IO) {
                    try { db.use { it.register(name,email,phone,password) } }
                    catch (_: SQLiteConstraintException) { null }
                }
                binding.submit.isEnabled = true
                if (id == null) binding.email.error = getString(R.string.duplicate) else openShop(id)
            }
        }
    }
}
