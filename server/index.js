const express = require('express')
const app = express()

app.use(express.json())

// Almacenamiento en memoria
let datos = []

// GET /datos — devuelve todos los datos guardados
app.get('/datos', (req, res) => {
    res.json({ datos })
})

// POST /datos — guarda un nuevo dato
app.post('/datos', (req, res) => {
    const body = req.body

    if (!body || Object.keys(body).length === 0) {
        return res.status(400).json({ error: 'Body vacío o inválido' })
    }

    const nuevo = {
        id: datos.length + 1,
        ...body,
        fecha: new Date().toISOString()
    }

    datos.push(nuevo)
    console.log('Dato guardado:', nuevo)

    res.status(201).json({ mensaje: 'Guardado correctamente', dato: nuevo })
})

// DELETE /datos — limpia todos los datos (útil para pruebas)
app.delete('/datos', (req, res) => {
    datos = []
    res.json({ mensaje: 'Datos eliminados' })
})

const PORT = process.env.PORT || 3000
app.listen(PORT, () => {
    console.log(`Servidor corriendo en puerto ${PORT}`)
})
