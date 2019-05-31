package de.zalando.beard.filter.implementations

import java.time.{ ZoneId, LocalDateTime, LocalDate, OffsetDateTime, Instant }
import java.time.format.DateTimeFormatter

import de.zalando.beard.filter._

import scala.collection.immutable.Map
import scala.util.matching.Regex

/**
 * @author rweyand
 */
class DateFormatFilter extends Filter {
  // {{ now | date format=format.Variable}}
  override def name = "date"

  case class DateFormatNotSupportedException(formatString: String) extends FilterException(formatString)

  override def apply(value: String, parameters: Map[String, Any]): String =
    parameters.get("format") match {
      // format given as static string in the template
      case Some(format: String) => {
        val dateTimeFormatter = getDateTimeFormatter(format)
        resolveDateFormatting(value, dateTimeFormatter)
      }
      // format given as variable (resolves to nested Option)
      case Some(Some(format)) => {
        val dateTimeFormatter = getDateTimeFormatter(format.asInstanceOf[String])
        resolveDateFormatting(value, dateTimeFormatter)
      }
      case Some(thing) => throw WrongParameterTypeException("format", "String")
      case None => throw ParameterMissingException("format")
    }

  def resolveDateFormatting(value: String, formatOut: DateTimeFormatter): String = {
    // All formatters supported by DateTimeFormatter may be added in a form:
    //  """REGEX""" -> "FORMATTER"
    // Grouping is not allowed: '(', ')' chars must be escaped, if used
    val datePatterns: Map[String, String] = Map(
      // 981173106
      """\d{9,10}""" -> "EPOCH",
      // 981173106987
      """\d{12,13}""" -> "EPOCH_MILLI",
      // 20010203
      """\d{8}""" -> "yyyyMMdd",
      // 2001-02-03 04:05:06
      """\d{4}-\d\d-\d\d \d\d:\d\d:\d\d""" -> "yyyy-MM-dd HH:mm:ss",
      // 2001-02-03 04:05:06+01:00
      """\d{4}-\d\d-\d\d \d\d:\d\d:\d\d[+\-]?\d\d:?\d\d""" -> "yyyy-MM-dd HH:mm:ssZ",
      // 2001-02-03
      """\d{4}-\d\d-\d\d""" -> "yyyy-MM-dd",
      // 2001-02-03+04:00
      """\d{4}-\d\d-\d\d[+\-]?\d\d:?\d\d""" -> "ISO_OFFSET_DATE",
      // 2001-02-03T04:05:06
      """\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d""" -> "ISO_LOCAL_DATE_TIME",
      // 2001-02-03T04:05:06+01:00'
      """\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d[+\-]?\d\d:?\d\d""" -> "ISO_OFFSET_DATE_TIME",
      // '2001-02-03T04:05:06.789Z'
      """\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{1,3}Z""" -> "ISO_INSTANT",
      // '2001-02-03T04:05:06Z'
      """\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ""" -> "ISO_INSTANT",
      // 2001-2-13
      """\d{4}-\d-\d\d""" -> "yyyy-M-dd",
      // 2001-12-3
      """\d{4}-\d\d-\d""" -> "yyyy-MM-d",
      // 2001-2-3
      """\d{4}-\d-\d""" -> "yyyy-M-d",
      // 03-02-2001
      """\d\d-\d\d-\d{4}""" -> "dd-MM-yyyy",
      // 03-02-2001 04:05:06
      """\d\d-\d\d-\d{4} \d\d:\d\d:\d\d""" -> "dd-MM-yyyy HH:mm:ss",
      // 3-12-2001
      """\d-\d\d-\d{4}""" -> "d-MM-yyyy",
      // 13-2-2001
      """\d\d-\d-\d{4}""" -> "dd-M-yyyy",
      // 3-2-2001
      """\d-\d-\d{4}""" -> "d-M-yyyy",
      // 04:05:06
      """\d\d:\d\d:\d\d""" -> "HH:mm:ss")

    val pattern = new Regex(datePatterns.keys.mkString("^((", ")|(", "))$"))
    val a = pattern.findFirstMatchIn(value)

    if (a.isDefined) {
      val patternIndex = a.get.subgroups.indexOf(value, 1)
      val formatIn = datePatterns.slice(patternIndex - 1, patternIndex).values.mkString

      return formatIn match {
        case "EPOCH" => getFormatFromEpoch(value, formatOut)
        case "EPOCH_MILLI" => getFormatFromMillis(value, formatOut)
        case "ISO_INSTANT" => getFormatFromInstant(value, formatOut)
        case "ISO_LOCAL_DATE_TIME" => getFormatFromLocal(value, formatOut)
        case "ISO_OFFSET_DATE_TIME" => getFormatFromOffset(value, formatOut)
        case "ISO_OFFSET_DATE" => getFormatFromOffsetDate(value, formatOut)
        case _ => getFormatFromPattern(value, formatIn, formatOut)
      }
    }

    throw new DateFormatNotSupportedException(value)
  }

  def getFormatFromMillis(millisAsString: String, formatter: DateTimeFormatter): String = {
    formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(millisAsString.toLong), ZoneId.of("GMT")))
  }

  def getFormatFromEpoch(epoch: String, formatter: DateTimeFormatter): String = {
    formatter.format(LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch.toLong), ZoneId.of("GMT")))
  }

  def getFormatFromInstant(dateSrc: String, formatter: DateTimeFormatter): String = {
    formatter.format(LocalDateTime.ofInstant(Instant.parse(dateSrc), ZoneId.systemDefault()))
  }

  def getFormatFromLocal(dateSrc: String, formatter: DateTimeFormatter): String = {
    formatter.format(LocalDateTime.parse(dateSrc))
  }

  def getFormatFromOffset(dateSrc: String, formatter: DateTimeFormatter): String = {
    formatter.format(OffsetDateTime.parse(dateSrc))
  }

  def getFormatFromOffsetDate(dateSrc: String, formatter: DateTimeFormatter): String = {
    formatter.format(LocalDate.parse(dateSrc, DateTimeFormatter.ISO_OFFSET_DATE))
  }

  def getFormatFromPattern(dateSrc: String, formatIn: String, formatter: DateTimeFormatter): String = {
    try {
      formatter.format(DateTimeFormatter.ofPattern(formatIn).parse(dateSrc))
    } catch {
      case e: Exception => throw new DateFormatNotSupportedException(formatIn)
    }
  }

  def getDateTimeFormatter(format: String): DateTimeFormatter = {
    try {
      DateTimeFormatter.ofPattern(format)
    } catch {
      case e: Exception => throw new DateFormatNotSupportedException(format)
    }
  }
}

object DateFormatFilter {

  def apply(): DateFormatFilter = new DateFormatFilter()
}
