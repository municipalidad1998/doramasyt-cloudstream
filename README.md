# DoramasYT - CloudStream Extension 🎭

Extensión de CloudStream 3 para ver **K-Dramas, C-Dramas, J-Dramas, Thai-Dramas y Películas** desde [DoramasYT](https://www.doramasyt.com) con subtítulos en español y audio latino.

---

## 📦 Instalación del Repositorio en CloudStream

1. Abre CloudStream 3 en tu dispositivo Android
2. Ve a **Configuración → Repositorios → Agregar repositorio**
3. Pega la siguiente URL:

```
https://raw.githubusercontent.com/municipalidad1998/doramasyt-cloudstream/builds/repo.json
```

> ⚠️ Esta URL ya contiene tu usuario (`municipalidad1998`)

4. Guarda y busca **"DoramasYT"** en la lista de extensiones
5. Instala la extensión

---

## 🗂️ Estructura del Proyecto

```
DoramasYT-CloudStream/
├── .github/
│   └── workflows/
│       └── build.yml              # CI/CD: compilación automática
├── DoramasYTProvider/
│   ├── build.gradle.kts           # Dependencias y metadata del plugin
│   └── src/main/kotlin/
│       └── com/doramasyt/
│           ├── DoramasYTPlugin.kt # Entry point del plugin
│           └── DoramasYTProvider.kt # Lógica de scraping
├── build.gradle.kts               # Build raíz del proyecto
├── settings.gradle.kts            # Módulos incluidos
├── repo.json                      # Definición del repositorio (rama builds)
└── plugins.json                   # Lista de plugins (rama builds)
```

---

## 🎬 Características

| Característica | Estado |
|---|---|
| Página principal con secciones | ✅ |
| Búsqueda de series y películas | ✅ |
| Detalle de serie con todos los episodios | ✅ |
| Detalle de películas | ✅ |
| Extracción de links de video | ✅ |
| K-Drama, C-Drama, J-Drama, Thai-Drama | ✅ |
| Películas asiáticas | ✅ |
| Idioma: Español | ✅ |

---

## 🛠️ Compilar el Proyecto

### Prerrequisitos
- **Android Studio** o **IntelliJ IDEA** con el plugin de Kotlin
- **JDK 11** o superior
- **Android SDK** (API 33)

### Pasos

```bash
# 1. Clonar el repositorio
git clone https://github.com/municipalidad1998/doramasyt-cloudstream.git
cd doramasyt-cloudstream

# 2. Compilar el plugin (genera el .cs3 y plugins.json)
./gradlew make makePluginsJson

# Los archivos compilados estarán en:
# ./builds/DoramasYT.cs3
# ./builds/plugins.json
```

---

## 🚀 Publicar en GitHub

### Primera vez

```bash
git init
git add .
git commit -m "feat: add DoramasYT CloudStream extension"
git branch -M main
git remote add origin https://github.com/municipalidad1998/doramasyt-cloudstream.git
git push -u origin main
```

GitHub Actions compilará automáticamente y publicará en la rama `builds`.

### URL final del repositorio

```
https://raw.githubusercontent.com/municipalidad1998/doramasyt-cloudstream/builds/repo.json
```

---

## 📌 Aviso Legal

Este proyecto es solo para fines educativos. No almacena ningún video en sus servidores. Todo el contenido es propiedad de sus respectivos dueños.

