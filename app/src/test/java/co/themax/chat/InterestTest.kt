package co.themax.chat

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class InterestTest {
    @Test fun countsEachDayOnceAndFollowsSelectedContact() {
        val start = LocalDateTime.of(2026, 1, 1, 12, 0)
        val rows = (0..4).flatMap { d ->
            val t = start.plusDays(d.toLong())
            listOf(Message(t,"A","Hola"), Message(t.plusMinutes(1),"B",if(d < 2) "¿Qué tal?" else "Bien"),
                Message(t.plusMinutes(2),"A","Dale"), Message(t.plusMinutes(3),"B",if(d < 2) "¿Y tú?" else "Bueno"))
        }
        val data = interestDays((rows + Message(start.plusDays(6),"B","Llamada perdida")).reversed())
        assertTrue(data.tile("B","Contacto").detail.startsWith("40 %"))
        assertTrue(data.tile("A","Contacto").detail.startsWith("80 %"))
        assertTrue(interestDays(rows.dropLast(1)).tile("B","Contacto").detail.contains("insuficientes"))
    }
}
