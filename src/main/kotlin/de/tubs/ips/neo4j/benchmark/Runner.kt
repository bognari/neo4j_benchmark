package de.tubs.ips.neo4j.benchmark

import org.neo4j.driver.v1.AuthTokens
import org.neo4j.driver.v1.Driver
import org.neo4j.driver.v1.GraphDatabase
import java.util.concurrent.TimeUnit

enum class Mode {
    NORMAL, SHARED, PARALLEL
}

enum class Func {
    dualID, dualLabel, strongID, strongLabel, normal
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
        for (func in Func.values()) {
            Mode.values()
                    .asSequence()
                    .filterNot { func == Func.normal && it != Mode.NORMAL }
                    .forEach { simulation(driver, func, it, query, index) }
        }
    }
}

fun simulation(driver: Driver, func: Func, mode: Mode, query: String, index: Int) {
    print("$index, ")
    print("$func, ")
    print("$mode")

    run(driver, func, mode, query)
    println()

}

fun header() {
    print("index, func, mode")
    for (i in 1..runs) {
        print(", ra $i")
        print(", rc $i")
        print(", ra + rc $i")
        print(", time $i")
        print(", number $i")
    }
}

fun run(driver: Driver, func: Func, mode: Mode, query: String) {
    for (i in 1..runs) {
        driver.session().use {
            val time = System.currentTimeMillis()

            val result =
                    if (func != Func.normal) {
                        it.run("CALL simulation.$func(\"$query\", \"$mode\")")
                    } else {
                        it.run(query)
                    }

            val number = result.list().size
            val sum = result.consume()
            val ra = sum.resultAvailableAfter(TimeUnit.MILLISECONDS)
            val rc = sum.resultConsumedAfter(TimeUnit.MILLISECONDS)

            print(", $ra")
            print(", $rc")
            print(", ${ra + rc}")
            print(", ${System.currentTimeMillis() - time}")
            print(", $number")
        }
    }
}
