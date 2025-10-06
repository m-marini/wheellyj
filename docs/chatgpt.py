import sounddevice as sd
import queue
import numpy as np
import whisper
from openai import OpenAI

# ===============================
# CONFIGURAZIONE
# ===============================
API_KEY = "LA_TUA_API_KEY"   # <-- inserisci qui la tua API key OpenAI
client = OpenAI(api_key=API_KEY)

# Modello Whisper per Speech-to-Text
stt_model = whisper.load_model("small")  # puoi usare "tiny" se vuoi più velocità

# Parametri microfono
samplerate = 16000
blocksize = 4000
channels = 1

# Coda per i dati audio
audio_queue = queue.Queue()

# ===============================
# FUNZIONI
# ===============================

def audio_callback(indata, frames, time, status):
    """Callback che prende l'audio dal microfono e lo mette in coda"""
    if status:
        print(status)
    audio_queue.put(indata.copy())

def ascolta_e_trascrivi(durata=5):
    """Ascolta dal microfono per X secondi e trascrive con Whisper"""
    print(f"🎙️ Sto ascoltando per {durata} secondi... parla!")
    registrazione = []

    with sd.InputStream(samplerate=samplerate,
                        channels=channels,
                        callback=audio_callback):
        sd.sleep(int(durata * 1000))  # ascolta per X secondi
        while not audio_queue.empty():
            registrazione.append(audio_queue.get())

    # Concatena in un array numpy
    audio = np.concatenate(registrazione, axis=0).flatten()

    # Salva temporaneamente su file WAV
    import soundfile as sf
    sf.write("temp.wav", audio, samplerate)

    # Trascrizione con Whisper
    result = stt_model.transcribe("temp.wav", language="it")
    return result["text"]

def chiedi_a_chatgpt(comando_voce):
    """Invia il comando vocale a ChatGPT e riceve il JSON di controllo"""
    prompt = f"""
    Il robot ha ricevuto questo comando vocale: "{comando_voce}".
    Converti in istruzione formale JSON, es:
    {{"azione": "muovi", "cella": [2,4]}}
    """
    risposta = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[
            {"role": "system", "content": "Sei il controllore del robot."},
            {"role": "user", "content": prompt}
        ]
    )
    return risposta.choices[0].message.content

# ===============================
# LOOP PRINCIPALE
# ===============================
if __name__ == "__main__":
    testo = ascolta_e_trascrivi(durata=5)
    print("🗣️ Hai detto:", testo)

    json_cmd = chiedi_a_chatgpt(testo)
    print("🤖 Comando JSON da ChatGPT:", json_cmd)
