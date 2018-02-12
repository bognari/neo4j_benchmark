package de.tubs.ips.neo4j.benchmark

import org.neo4j.driver.v1.AuthTokens
import org.neo4j.driver.v1.Driver
import org.neo4j.driver.v1.GraphDatabase
import java.util.concurrent.TimeUnit

enum class Mode {
    NORMAL, SHARED, PARALLEL
}

@SuppressWarnings()
enum class Func {
    dualID, dualLabel, strongID, strongLabel
}

val runs = 2

val queries = listOf("""
    MATCH () RETURN count(*);
""".trimIndent(),
        """MATCH
  (x:FullProfessor)-[:worksFor]-({id: 'http://www.Department0.University99.edu'})
OPTIONAL MATCH
  (y)-[:advisor]-(x),
  (x)-[:teacherOf]-(z),
  (y)-[:takesCourse]-(z)
RETURN x, y, z;""",
        """MATCH
  (x:FullProfessor)-[:worksFor]-({id: 'http://www.Department0.University12.edu'})
OPTIONAL MATCH
  (y)-[:advisor]-(x),
  (x)-[:teacherOf]-(z),
  (y)-[:takesCourse]-(z)
RETURN x, y, z;""",
        """MATCH
  (x:FullProfessor)-[:worksFor]-({id: 'http://www.Department0.University12.edu'})
RETURN x, x.emailAddress, x.telephone, x.name;""",
        """MATCH
  (st)-[:teachingAssistantOf]-(course)
MATCH
  (prof)-[:teacherOf]-(course),
  (st)-[:advisor]-(prof)
OPTIONAL MATCH
  (st)-[:takesCourse]-(course2),
  (pub1)-[:publicationAuthor]-(st)
OPTIONAL MATCH
  (pub2)-[:publicationAuthor]-(prof)
RETURN st, course, prof, course2, pub1, pub2;""",
        """MATCH
  (pub:Publication)-[:publicationAuthor]-(st),
  (pub)-[:publicationAuthor]-(prof)
MATCH
  (st)-[:undergraduateDegreeFrom]-(univ),
  (dept)-[:subOrganizationOf]-(univ)
MATCH
  (st)-[:memberOf]-(dept),
  (prof)-[:worksFor]-(dept)
OPTIONAL MATCH
  (head)-[:headOf]-(dept),
  (others)-[:worksFor]-(dept)
OPTIONAL MATCH
  (prof)-[:doctoralDegreeFrom]-(univ1)
RETURN pub, st, st.emailAddress, st.telephone, prof, univ, dept, head, others, univ1;""",
        """MATCH
  (pub)-[:publicationAuthor]-(st:GraduateStudent),
  (pub)-[:publicationAuthor]-(prof:FullProfessor)
MATCH
  (st)-[:advisor]-(prof)
MATCH
  (st)-[:memberOf]-(dept),
  (prof)-[:worksFor]-(dept)
OPTIONAL MATCH
  (st)-[:undergraduateDegreeFrom]-(univ1)
OPTIONAL MATCH
  (prof)-[:doctoralDegreeFrom]-(univ)
OPTIONAL MATCH
  (head)-[:headOf]-(dept),
  (others)-[:worksFor]-(dept)
RETURN pub, st, prof, dept, univ1, univ, head, others;""").map { it.replace("\n", " ") }

fun main(args: Array<String>) {

    if (args.size < 3) {
        println("start with <uri> <user> <password>")
        return
    }

    val driver = GraphDatabase.driver(args[0], AuthTokens.basic(args[1], args[2]))

    header()
    println()
    
    for ((index, query) in queries.withIndex()) {
        print("normal, ")
        print("$index, ")
        print("\"$query\"")
        run(driver, query)
        println()
        for (func in Func.values()) {
            simulation(driver, func, query, index)
        }
    }
}

fun simulation(driver: Driver, func: Func, query: String, index: Int) {
    for (mode in Mode.values()) {
        print("$func $mode, ")
        print("$index, ")
        print("\"$query\"")
        run(driver, "CALL simulation.$func(\"$query\", \"$mode\")")
        println()
    }
}

fun header() {
    print("func, index, query ")
    for (i in 1..runs) {
        print(", ra $i")
        print(", rc $i")
        print(", time $i")
    }
}

fun run(driver: Driver, query: String) {
    for (i in 1..runs) {
        driver.session().use {
            val time = System.currentTimeMillis()
            val summary = it.run(query).consume()
            print(", ${summary.resultAvailableAfter(TimeUnit.MILLISECONDS)}")
            print(", ${summary.resultConsumedAfter(TimeUnit.MILLISECONDS)}")
            print(", ${System.currentTimeMillis() - time}")
        }
    }
}
