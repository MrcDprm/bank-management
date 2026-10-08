# Online Banka Yönetimi

[English](README.md) | **Türkçe**

Java ve JavaFX ile yazılmış, iki taraflı bir masaüstü bankacılık uygulaması. Şube panelinde personel müşteri ve hesap açar; internet bankacılığı tarafında müşteri kendi parasını yönetir. Veriler yerelde SQLite'ta tutulur.

> 🚧 Geliştirme sürüyor. Bu README proje planıdır, v1.0.0'da tamamlanacak.

## Plan

### MVP
- **İki taraf, rollerle giriş:**
  - Şube personeli: gişe ve yönetici.
  - Müşteri: internet bankacılığı.
  - Şifreler **Argon2id** ile hash'lenir ve güçlü şifre kurallarına uymalıdır.
  - Art arda hatalı denemede hesap kilitlenir.
  - Yetkiler servis katmanında kontrol edilir.
- **Şube paneli:**
  - Müşteri (sağlama basamaklı T.C. kimlik no, iletişim bilgileri) ve hesap açma.
  - Müşteri adına nakit yatırma ve çekme.
  - Hesap dondurma ve kapatma.
  - Müşteri şifresini sıfırlama: müşteri geçici şifre alır ve ilk girişte değiştirmek zorundadır.
- **Müşteri internet bankacılığı:**
  - Hesaplar ve bakiyeler.
  - IBAN ile para transferi: kendi hesapları arasında ya da başka müşterilere.
  - Arama ve tarih filtreli işlem geçmişi.
- **Doğru para hesabı:**
  - Tutarlar kuruş cinsinden tamsayı olarak tutulur, asla kayan noktalı sayı kullanılmaz.
  - Her transfer tek işlemdir: para bir hesaptan çıkıp diğerine ulaşmadan kaybolamaz.
  - Bakiye eksiye düşemez.
- **IBAN:** TR formatında, her hesap için üretilir ve mod-97 kontrolüyle doğrulanır.
- **Masaüstü uygulaması:**
  - Koyu ve açık tema (AtlantaFX), Türkçe ve İngilizce.
  - İkon, sürüm, Hakkında penceresi.
  - Veriler kullanıcı klasöründe, Windows kurulum dosyası.
- **Testler:** IBAN, para hesabı, transfer, limitler, faiz ve yetkiler; JUnit ile.

### Ekler
- **Hesap türleri:**
  - Vadesiz hesap.
  - Faiz hesaplı vadeli hesap.
  - Sabit demo kurlarıyla çevrilen döviz hesapları (USD, EUR).
- **Hesap özeti:** tarih aralığı için hesap özeti ve her transfer için dekont, PDF olarak.
- **Harcama grafikleri:** kategoriye ve aya göre gelir ve gider.
- **Kayıtlı alıcılar ve limitler:**
  - Sık kullanılan alıcılar.
  - Günlük transfer limiti.
  - Büyük transferde ek onay.
- **Örnek veri:** ilk açılışta bir kez sorulur (müşteriler, hesaplar ve birkaç aylık işlem).

### Gelecek Planları
- İleri tarihli ve düzenli transferler.
- Fatura ödemeleri.
- Kartlar ve kart ekstreleri.
- Canlı döviz kurları.

## Kullanılan Teknolojiler
- Java, JavaFX, AtlantaFX, Ikonli
- SQLite (sqlite-jdbc), Bouncy Castle (Argon2id)
- Maven, JUnit
- jpackage, Inno Setup
