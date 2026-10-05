package com.ksm.draftcsrapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ksm.draftcsrapp.databinding.ActivityLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sudah login (token masih valid) -> langsung ke MainActivity
        if (tokenManager.hasValidToken()) {
            goToMain()
            return
        }

        // Inisialisasi View Binding
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeViewModel()

        // Access token habis tapi refresh token ada -> coba perbarui diam-diam
        if (tokenManager.getRefreshToken() != null) {
            showLoading(true)
            lifecycleScope.launch {
                if (authRepository.refreshSession()) goToMain() else showLoading(false)
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (username.isNotEmpty() && password.isNotEmpty()) {
                viewModel.performLogin(username, password)
            } else {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = "Username dan password tidak boleh kosong"
            }
        }
    }

    private fun observeViewModel() {
        // Mengamati StateFlow secara aman mengikuti lifecycle Activity
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loginState.collect { state ->
                    when (state) {
                        is LoginUiState.Idle -> {
                            showLoading(false)
                            binding.tvError.visibility = View.GONE
                        }
                        is LoginUiState.Loading -> {
                            showLoading(true)
                            binding.tvError.visibility = View.GONE
                        }
                        is LoginUiState.Success -> {
                            showLoading(false)
                            // Arahkan ke MainActivity atau halaman List Draft CSR IT
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        }
                        is LoginUiState.Error -> {
                            showLoading(false)
                            binding.tvError.visibility = View.VISIBLE
                            binding.tvError.text = state.message
                        }
                    }
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.btnLogin.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.etUsername.isEnabled = !isLoading
        binding.etPassword.isEnabled = !isLoading
    }
}