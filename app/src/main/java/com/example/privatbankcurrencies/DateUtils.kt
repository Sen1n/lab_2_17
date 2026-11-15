package com.example.privatbankcurrencies

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {

    private const val PATTERN = "dd.MM.yyyy"

    fun todayString(): String {
        val formatter = SimpleDateFormat(PATTERN, Locale.US)
        return formatter.format(Date())
    }

    fun validateArchiveDate(dateStr: String): String? {
        if (!Regex("""^\d{2}\.\d{2}\.\d{4}$""").matches(dateStr)) {
            return "Некоректний формат. Введіть дату у форматі дд.мм.рррр"
        }

        val formatter = SimpleDateFormat(PATTERN, Locale.US).apply {
            isLenient = false
        }

        val parsedDate = try {
            formatter.parse(dateStr)
        } catch (e: ParseException) {
            null
        } ?: return "Такої дати не існує в календарі"

        val selectedCal = Calendar.getInstance().apply {
            time = parsedDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (selectedCal.after(todayCal)) {
            return "Не можна обирати майбутню дату"
        }

        val minCal = (todayCal.clone() as Calendar).apply {
            add(Calendar.YEAR, -4)
        }

        if (selectedCal.before(minCal)) {
            return "Архів ПриватБанку доступний лише за останні 4 роки"
        }

        return null
    }

    fun millisToDateString(millis: Long): String {
        val formatter = SimpleDateFormat(PATTERN, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date(millis))
    }

    fun dateStringToMillis(dateStr: String): Long? {
        val formatter = SimpleDateFormat(PATTERN, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        return try {
            formatter.parse(dateStr)?.time
        } catch (e: ParseException) {
            null
        }
    }
}