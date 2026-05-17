package com.jonathanquinto.conversioncapital

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // ─── VISTAS ───────────────────────────────────────────────
    private lateinit var edtMonto: EditText
    private lateinit var spinnerOrigen: Spinner
    private lateinit var spinnerDestino: Spinner
    private lateinit var btnConvertir: Button
    private lateinit var btnLimpiar: Button
    private lateinit var btnIntercambiar: Button
    private lateinit var switchSonido: Switch
    private lateinit var seekBarNivel: SeekBar
    private lateinit var progressBar: ProgressBar
    private lateinit var txtResultado: TextView
    private lateinit var txtNivelMultiplicador: TextView

    // ─── DATOS ────────────────────────────────────────────────
    // Tasas de cambio relativas al USD (1 USD = X moneda)
    private val tasas = mapOf(
        "USD 🇺🇸" to 1.0,
        "EUR 🇪🇺" to 0.9215,
        "PAB 🇵🇦" to 1.0,
        "GBP 🇬🇧" to 0.7892,
        "JPY 🇯🇵" to 157.42,
        "CAD 🇨🇦" to 1.3645,
        "AUD 🇦🇺" to 1.5310,
        "CHF 🇨🇭" to 0.8974,
        "CNY 🇨🇳" to 7.2458,
        "MXN 🇲🇽" to 17.1500,
        "BRL 🇧🇷" to 5.0820,
        "COP 🇨🇴" to 3968.00,
        "ARS 🇦🇷" to 878.50,
        "CLP 🇨🇱" to 942.30,
        "PEN 🇵🇪" to 3.7200,
        "CRC 🇨🇷" to 519.80,
        "HNL 🇭🇳" to 24.7500,
        "GTQ 🇬🇹" to 7.7800,
        "DOP 🇩🇴" to 58.9200,
        "INR 🇮🇳" to 83.4500,
        "KRW 🇰🇷" to 1342.00,
        "SAR 🇸🇦" to 3.7500,
        "AED 🇦🇪" to 3.6725,
        "SGD 🇸🇬" to 1.3410,
        "HKD 🇭🇰" to 7.8210,
        "NOK 🇳🇴" to 10.5600,
        "SEK 🇸🇪" to 10.4200,
        "NZD 🇳🇿" to 1.6290
    )

    private val monedas: List<String> by lazy { tasas.keys.toList() }

    // DecimalFormat fijo en Locale.US para evitar NumberFormatException por locale
    private val df = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

    // Nivel del SeekBar: empieza en 100 (= multiplicador 1.0 = sin efecto)
    private var nivelMultiplicador: Double = 1.0

    // ─── LIFECYCLE ────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inicializarVistas()
        configurarSpinners()
        configurarSeekBar()
        configurarBotones()
        configurarSwitch()
    }

    // ─── INICIALIZAR VISTAS ───────────────────────────────────
    private fun inicializarVistas() {
        edtMonto        = findViewById(R.id.edtMonto)
        spinnerOrigen   = findViewById(R.id.spinnerOrigen)
        spinnerDestino  = findViewById(R.id.spinnerDestino)
        btnConvertir    = findViewById(R.id.btnConvertir)
        btnLimpiar      = findViewById(R.id.btnLimpiar)
        btnIntercambiar = findViewById(R.id.btnIntercambiar)
        switchSonido    = findViewById(R.id.switchSonido)
        seekBarNivel    = findViewById(R.id.seekBarNivel)
        progressBar     = findViewById(R.id.progressBar)
        txtResultado    = findViewById(R.id.txtResultado)
        txtNivelMultiplicador    = findViewById(R.id.txtNivelMultiplicador)
    }

    // ─── CONFIGURAR SPINNERS ──────────────────────────────────
    private fun configurarSpinners() {
        val adapter = ArrayAdapter(
            this,
            R.layout.spinner_item_personalizado,
            monedas
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_item) }

        spinnerOrigen.adapter  = adapter
        spinnerDestino.adapter = adapter

        spinnerOrigen.setSelection(monedas.indexOf("USD 🇺🇸"))
        spinnerDestino.setSelection(monedas.indexOf("EUR 🇪🇺"))
    }

    // ─── CONFIGURAR SEEKBAR ───────────────────────────────────
    // SeekBar va de 0 a 100. progress=100 → multiplicador=1.0 (conversión normal).
    // Se inicializa en 100 para que coincida con nivelMultiplicador = 1.0.
    private fun configurarSeekBar() {
        seekBarNivel.progress = 100
        seekBarNivel.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // Mínimo 1% para no multiplicar por cero
                nivelMultiplicador = if (progress == 0) 0.01 else progress / 100.0
                txtNivelMultiplicador.text = getString(R.string.txt_seekbar_valor, nivelMultiplicador)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    // ─── CONFIGURAR BOTONES ───────────────────────────────────
    private fun configurarBotones() {
        btnConvertir.setOnClickListener    { convertir() }
        btnLimpiar.setOnClickListener      { limpiar() }
        btnIntercambiar.setOnClickListener { intercambiar() }
    }

    // ─── CONVERTIR ────────────────────────────────────────────
    private fun convertir() {
        val montoStr = edtMonto.text.toString().trim()

        if (montoStr.isEmpty()) {
            mostrarToast(getString(R.string.msg_ingrese_monto))
            return
        }

        // Reemplazar coma por punto para soportar ambos formatos de entrada
        val montoNormalizado = montoStr.replace(',', '.')
        val monto = montoNormalizado.toDoubleOrNull()
        if (monto == null || monto <= 0.0) {
            mostrarToast(getString(R.string.msg_valor_invalido))
            return
        }

        val origen  = spinnerOrigen.selectedItem.toString()
        val destino = spinnerDestino.selectedItem.toString()
        if (origen == destino) {
            mostrarToast(getString(R.string.msg_monedas_diferentes))
            return
        }

        // Convertir via USD como moneda base
        val tasaOrigen  = tasas[origen]  ?: 1.0
        val tasaDestino = tasas[destino] ?: 1.0
        val montoUSD    = monto / tasaOrigen
        val resultado   = montoUSD * tasaDestino * nivelMultiplicador
        val montoFormato = df.format(monto)
        val resultadoFormato = df.format(resultado)


        val codigoOrigen  = origen.take(3)
        val codigoDestino = destino.take(3)
        txtResultado.text = getString( R.string.txt_resultado_final,montoFormato,codigoOrigen, resultadoFormato, codigoDestino)

        animarProgressBar()

        if (switchSonido.isChecked) {
            reproducirSonido()
        }
        mostrarToast(getString(R.string.msg_conversion_realizada))
    }

    // ─── LIMPIAR ──────────────────────────────────────────────
    private fun limpiar() {
        edtMonto.text.clear()
        txtResultado.text = getString(R.string.txt_resultado_inicial)
        progressBar.progress = 0
        // Resetear SeekBar a 100 para que coincida con multiplicador = 1.0
        seekBarNivel.progress = 100
        nivelMultiplicador = 1.0
        spinnerOrigen.setSelection(monedas.indexOf("USD 🇺🇸"))
        spinnerDestino.setSelection(monedas.indexOf("EUR 🇪🇺"))
        mostrarToast(getString(R.string.msg_campos_limpiados))
    }

    // ─── INTERCAMBIAR ─────────────────────────────────────────
    private fun intercambiar() {
        val posOrigen  = spinnerOrigen.selectedItemPosition
        val posDestino = spinnerDestino.selectedItemPosition
        spinnerOrigen.setSelection(posDestino)
        spinnerDestino.setSelection(posOrigen)
        if(edtMonto.text.isNotEmpty()){
            btnConvertir.performClick()
            mostrarToast(getString(R.string.msg_monedas_intercambiadas))
        }
        else{
            mostrarToast(getString(R.string.msg_ingrese_monto))
        }
    }

    // ─── ANIMACIÓN PROGRESSBAR ────────────────────────────────
    private fun animarProgressBar() {
        progressBar.progress = 0
        val handler = Handler(Looper.getMainLooper())
        var progreso = 0
        val runnable = object : Runnable {
            override fun run() {
                if (progreso <= 100) {
                    progressBar.progress = progreso
                    progreso += 5
                    handler.postDelayed(this, 30)
                }
            }
        }
        handler.post(runnable)
    }

    // ─── SONIDO ───────────────────────────────────────────────
    private fun reproducirSonido() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            Handler(Looper.getMainLooper()).postDelayed({ toneGen.release() }, 300)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun configurarSwitch() {
        // 1. Establecemos el texto inicial según el estado por defecto
        actualizarTextoSwitch(switchSonido.isChecked)

        // 2. Configuramos el listener para cambios futuros
        switchSonido.setOnCheckedChangeListener { _, isChecked ->
            actualizarTextoSwitch(isChecked)
        }
    }

    private fun actualizarTextoSwitch(estaActivado: Boolean) {
        switchSonido.text = if (estaActivado) {
            getString(R.string.txt_sonido)
        } else {
            getString(R.string.txt_sonido_desactivado)
        }
    }

    // ─── TOAST ────────────────────────────────────────────────
    private fun mostrarToast(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
    }
}
