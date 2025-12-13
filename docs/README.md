# 📚 Documentación - EstudiaApp

Índice centralizado de toda la documentación del proyecto.

---

## 📋 Documentos por Categoría

### 🗄️ Base de Datos y Arquitectura
- **[README_BBDD.md](README_BBDD.md)** - Estructura de Firestore, modelos de datos, relaciones

### 💾 Sistema de Caché
- **[CACHE_POLICY.md](CACHE_POLICY.md)** - Arquitectura TTL, snapshot listeners, debugging, future work
- **[CACHE_IMPLEMENTATION_SUMMARY.md](CACHE_IMPLEMENTATION_SUMMARY.md)** - Resumen de cambios, impacto de escalabilidad, testing

### ✅ Checklists y Completitud
- **[IMPLEMENTATION_COMPLETE.txt](IMPLEMENTATION_COMPLETE.txt)** - Overview visual de implementación completada

---

## 🎯 Documentos Recomendados por Rol

### Para Desarrolladores (Frontend/Android)
1. Leer: [CACHE_POLICY.md](CACHE_POLICY.md) - Entender cómo funciona el caché
2. Referencia: [README_BBDD.md](README_BBDD.md) - Estructura de datos en Firestore
3. Testing: [CACHE_IMPLEMENTATION_SUMMARY.md](CACHE_IMPLEMENTATION_SUMMARY.md) - Testing recommendations

### Para DevOps / Deployment
1. Leer: [IMPLEMENTATION_COMPLETE.txt](IMPLEMENTATION_COMPLETE.txt) - Estado y validación
2. Referencia: [CACHE_IMPLEMENTATION_SUMMARY.md](CACHE_IMPLEMENTATION_SUMMARY.md) - Escalabilidad

### Para Product Managers
1. Leer: [CACHE_IMPLEMENTATION_SUMMARY.md](CACHE_IMPLEMENTATION_SUMMARY.md) - Impacto y mejoras
2. Referencia: [IMPLEMENTATION_COMPLETE.txt](IMPLEMENTATION_COMPLETE.txt) - Estado del proyecto

---

## 🚀 Quick Links

- **[../README.md](../README.md)** - Documentación principal del proyecto
- **[../ANALISIS_RENTABILIDAD.md](../ANALISIS_RENTABILIDAD.md)** - Análisis de negocio
- **[../ANALISIS_ESCALABILIDAD.md](../ANALISIS_ESCALABILIDAD.md)** - Análisis técnico

---

## 📝 Contenido Rápido

### Cache Policy Highlights
```
TTL: 5 minutos
Storage: ConcurrentHashMap (thread-safe)
Real-time: Snapshot listeners en PlanDetailsActivity
Offline: Fallback automático a cache stale
```

### Base de Datos Highlights
```
Primary: Cloud Firestore
Storage: Firebase Storage (PDFs)
Auth: Firebase Authentication
Functions: Cloud Functions (Node.js 20)
```

---

## 🔄 Estructura del Proyecto

```
EstudiaApp/
├── README.md                              # Documentación principal
├── docs/                                  # 📁 CARPETA DE DOCUMENTACIÓN
│   ├── README.md                          # Este archivo (índice)
│   ├── README_BBDD.md                     # Base de datos
│   ├── CACHE_POLICY.md                    # Política de caché
│   ├── CACHE_IMPLEMENTATION_SUMMARY.md    # Resumen implementación
│   └── IMPLEMENTATION_COMPLETE.txt        # Checklist visual
├── ANALISIS_RENTABILIDAD.md               # Business analysis
├── ANALISIS_ESCALABILIDAD.md              # Technical analysis
├── app/                                   # Código fuente
├── functions/                             # Cloud Functions
└── ...
```

---

## ✨ Versión Actual

**v1.0 (beta/0.0.1)** - 13 de diciembre de 2025

Última implementación: **Política de Refresco de Caché con TTL y Snapshot Listeners**
- ✅ CachedPlan class con expiración automática
- ✅ Real-time sync via Firestore listeners
- ✅ Offline resilience con fallback cache stale
- ✅ 80% reducción en requests Firestore

---

## 📞 Contacto

Para preguntas sobre documentación específica:
- **Cache**: Revisar [CACHE_POLICY.md](CACHE_POLICY.md)
- **Base de datos**: Revisar [README_BBDD.md](README_BBDD.md)
- **Estado del proyecto**: Revisar [IMPLEMENTATION_COMPLETE.txt](IMPLEMENTATION_COMPLETE.txt)
