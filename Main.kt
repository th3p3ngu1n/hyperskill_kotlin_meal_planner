package mealplanner

import java.io.File
import java.sql.DriverManager
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import kotlin.use

enum class MenuAction(val label: String){
    ADD("add"),
    SHOW("show"),
    PLAN("plan"),
    SAVE("save"),
    EXIT("exit")
}

enum class MealCategory(val label: String) {
    BREAKFAST("breakfast"),
    LUNCH("lunch"),
    DINNER("dinner")
}

enum class DayOfWeek(val label: String) {
    MONDAY("Monday"),
    TUESDAY("Tuesday"),
    WEDNESDAY("Wednesday"),
    THURSDAY("Thursday"),
    FRIDAY("Friday"),
    SATURDAY("Saturday"),
    SUNDAY("Sunday")
}

class Meal(val category: MealCategory,
           val name: String,
           val ingredients: List<String> = emptyList(),
           val mealId: Int? = null) {
    override fun toString(): String {
        val ingredientsList = ingredients.joinToString("\n") { it }
        return "Name: $name\nIngredients:\n$ingredientsList"
    }
}

class Plan(val dayOfWeek: DayOfWeek, val mealOption: String, val category: MealCategory, val mealId: Int? = null) {
    fun getCategory(): String {
        return category.label.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

const val DB_URL = "jdbc:sqlite:meals.db"
const val MEALS_TABLE = "meals"
const val INGREDIENTS_TABLE = "ingredients"
const val PLANS_TABLE = "plan"

private fun createDatabase() {
    val connection: Connection = DriverManager.getConnection(DB_URL)
    val statement = connection.createStatement()
    statement.executeUpdate("""CREATE TABLE IF NOT EXISTS $MEALS_TABLE (
            meal_id INTEGER PRIMARY KEY, 
            category TEXT, 
            meal TEXT)
            """.trimMargin()
    )
    statement.executeUpdate("""CREATE TABLE IF NOT EXISTS $INGREDIENTS_TABLE (
            ingredient_id INTEGER PRIMARY KEY, 
            ingredient TEXT, 
            meal_id INTEGER, 
            FOREIGN KEY (meal_id) REFERENCES meals(meal_id))
            """.trimMargin()
    )
    statement.executeUpdate("""CREATE TABLE IF NOT EXISTS $PLANS_TABLE (
            plan_id INTEGER PRIMARY KEY, 
            day_of_week TEXT,
            meal_category TEXT,
            meal_id INTEGER, 
            FOREIGN KEY (meal_id) REFERENCES meals(meal_id))
            """.trimMargin()
    )
    statement.close()
    connection.close()
}

fun insertPlanForDay(dayOfWeek: DayOfWeek, meals: List<Meal>) {
    for (meal in meals) {
        try {
            val mealId = meal.mealId!!

            val connection: Connection = DriverManager.getConnection(DB_URL)
            val checkDayOfWeekSql = """
                SELECT 1
                FROM $PLANS_TABLE
                WHERE day_of_week = ?
                AND meal_category = ?
            """.trimIndent()
            val checkDayOfWeek = connection.prepareStatement(checkDayOfWeekSql)
            checkDayOfWeek.setString(1, dayOfWeek.label)
            checkDayOfWeek.setString(2, meal.category.label)

            var exists = false
            checkDayOfWeek.executeQuery().use { resultSet: ResultSet ->
                while (resultSet.next()) {
                    exists = true
                }
            }
            checkDayOfWeek.close()

            if (exists) {
                print("update")
                val updatePlanSql = """
                    UPDATE $PLANS_TABLE
                    SET meal_category = ?,
                    meal_id = ?
                    WHERE day_of_week = ?
                """.trimIndent()
                val updatePlan = connection.prepareStatement(updatePlanSql)
                updatePlan.setString(1, meal.category.label)
                updatePlan.setInt(2, mealId)
                updatePlan.setString(3, dayOfWeek.label)
                updatePlan.executeUpdate()
                updatePlan.close()
            } else {
                val insertPlanSql = """
                    INSERT INTO $PLANS_TABLE (day_of_week, meal_category, meal_id)
                    VALUES (?, ?, ?)
                """.trimIndent()
                val insertPlan = connection.prepareStatement(insertPlanSql)
                insertPlan.setString(1, dayOfWeek.label)
                insertPlan.setString(2, meal.category.label)
                insertPlan.setInt(3, mealId)
                insertPlan.executeUpdate()
                insertPlan.close()
            }

            connection.close()
        } catch (ex: SQLException) {
            println("Inserting plan for day " + ex.message)
        }
    }
    println("Yeah! We planned the meals for ${dayOfWeek.label}.")
}

fun getMealIdByNameAndCategory(name: String, category: String): Int? {
    try {
        val connection: Connection = DriverManager.getConnection(DB_URL)
        val getMealSql = """
            SELECT meal_id
            FROM $MEALS_TABLE
            WHERE meal = ?
            AND category = ?
        """.trimIndent()

        val getMeal = connection.prepareStatement(getMealSql)
        getMeal.setString(1, name)
        getMeal.setString(2, category)

        var mealId: Int? = null
        getMeal.executeQuery().use { resultSet: ResultSet ->
            while (resultSet.next()) {
                mealId = resultSet.getInt("meal_id")
            }
        }
        getMeal.close()
        connection.close()
        return mealId
    } catch (ex: SQLException) {
        println("Getting meals by name and category " + ex.message)
        return null
    }
}

fun getWeeklyPlan(): List<Plan> {
    try {
        val connection: Connection = DriverManager.getConnection(DB_URL)
        val getPlanSql = """
            SELECT p.day_of_week, 
            p.meal_category,
            m.meal,
            m.meal_id
            FROM $PLANS_TABLE AS p
            JOIN $MEALS_TABLE AS m
            ON p.meal_id = m.meal_id
        """.trimIndent()

        val getPlans = connection.prepareStatement(getPlanSql)
        val plans = mutableListOf<Plan>()
        getPlans.executeQuery().use { resultSet: ResultSet ->
            while (resultSet.next()) {
                val dayOfWeek = DayOfWeek.valueOf(resultSet.getString("day_of_week").uppercase())
                val category = MealCategory.valueOf(resultSet.getString("meal_category").uppercase())
                val name = resultSet.getString("meal")
                val mealId = resultSet.getInt("meal_id")

                plans.add(Plan(dayOfWeek, name, category, mealId))
            }
        }
        getPlans.close()
        connection.close()
        return plans
    } catch (ex: SQLException) {
        println("Getting weekly plan " + ex.message)
        return emptyList()
    }
}

fun getMealIngredients(mealId: Int): List<String> {
    try {
        val connection: Connection = DriverManager.getConnection(DB_URL)
        val getIngredientsSql = """
            SELECT ingredient
            FROM $INGREDIENTS_TABLE
            WHERE meal_id = ?
        """.trimIndent()
        val getIngredients = connection.prepareStatement(getIngredientsSql)
        getIngredients.setInt(1, mealId)
        var ingredients = mutableListOf<String>()
        getIngredients.executeQuery().use { resultSet: ResultSet ->
                while (resultSet.next()) {
                    val ingredient = resultSet.getString("ingredient")
                    ingredients.add(ingredient)
                }
            }
        getIngredients.close()
        connection.close()
        return ingredients
    } catch (ex: SQLException) {
        println("Getting meal ingredients " + ex.message)
        return emptyList()
    }
}

fun showMealPlan() {
    val plans = getWeeklyPlan()
    val groupedPlans = plans.groupBy { it.dayOfWeek }
    for (group in groupedPlans) {
        println(group.key.label)
        for (plan in group.value) {
            println("${plan.getCategory()}: ${plan.mealOption}")
        }
        println()
    }
}

fun insertMealAndIngredients(category: MealCategory, mealName: String, ingredients: List<String>): Meal? {
    try {
        val connection: Connection = DriverManager.getConnection(DB_URL)
        val insertMealSql = """
            INSERT INTO $MEALS_TABLE (category, meal)
            VALUES (?, ?)
        """.trimIndent()
        val insertMeal = connection.prepareStatement(insertMealSql, Statement.RETURN_GENERATED_KEYS)
        insertMeal.setString(1, category.label)
        insertMeal.setString(2, mealName)
        insertMeal.executeUpdate()

        val generatedKeys = insertMeal.generatedKeys
        var mealId: Int? = null
        if (generatedKeys.next()) {
            mealId = generatedKeys.getInt(1)
        }
        insertMeal.close()

        if (mealId != null) {
            for (ingredient in ingredients) {
                val insertIngredientSql = """
                    INSERT INTO $INGREDIENTS_TABLE (ingredient, meal_id)
                    VALUES (?, ?)
                """.trimIndent()
                val insertIngredient = connection.prepareStatement(insertIngredientSql)
                insertIngredient.setString(1, ingredient)
                insertIngredient.setInt(2, mealId)
                insertIngredient.executeUpdate()
                insertIngredient.close()
            }
        }
        connection.close()

        return Meal(category, mealName, ingredients, mealId)
    } catch (ex: SQLException) {
        println("Inserting meals " + ex.message)
        return null
    }
}

fun getMeals(category: MealCategory): List<Meal> {
    return try {
        val connection: Connection = DriverManager.getConnection(DB_URL)
        val getMealsSql = """
            SELECT * 
            FROM $MEALS_TABLE
            WHERE category = ?
            """.trimMargin()
        val getMeals = connection.prepareStatement(getMealsSql)
        getMeals.setString(1, category.label)

        val meals = mutableListOf<Meal>()
        getMeals.executeQuery().use { resultSet: ResultSet ->
            while(resultSet.next()) {
                val category = MealCategory.valueOf(resultSet.getString("category").uppercase())
                val name = resultSet.getString("meal")
                val mealId = resultSet.getInt("meal_id")
                if (mealId > 0) {
                    val ingredients = getIngredients(connection, mealId)
                    meals.add(Meal(category, name, ingredients, mealId))
                }
            }
        }
        getMeals.close()
        connection.close()
        return meals
    }
    catch (ex: SQLException) {
        println("Getting meals " + ex.message)
        return emptyList()
    }
}

fun getIngredients(connection: Connection, mealId: Int): List<String> {
    return try {
        val statement = connection.createStatement()
        val ingredients = mutableListOf<String>()
        statement.executeQuery("""
            SELECT *
            FROM $INGREDIENTS_TABLE
            WHERE meal_id = $mealId
            ORDER BY ingredient_id
        """.trimIndent()).use {
            resultSet: ResultSet ->
                while(resultSet.next()) {
                    val ingredient = resultSet.getString("ingredient")
                    ingredients.add(ingredient)
                }
        }
        statement.close()
        return ingredients
    } catch (ex: SQLException) {
        println(ex.message)
        return emptyList()
    }
}

fun getAction (): String {
    println("What would you like to do (${MenuAction.ADD.label}, ${MenuAction.SHOW.label}, ${MenuAction.PLAN.label}, ${MenuAction.SAVE.label}, ${MenuAction.EXIT.label})?")
    return readln().lowercase()
}

fun parseMealCategoryInput(): MealCategory {
    var category: MealCategory? = null
    while(category == null) {
        val input = readln().uppercase()
        try {
            category = MealCategory.valueOf(input)
        } catch(_: IllegalArgumentException) {
            println("Wrong meal category! Choose from: ${MealCategory.BREAKFAST.label}, ${MealCategory.LUNCH.label}, ${MealCategory.DINNER.label}.")
        }
    }
    return category
}

fun getMealCategory(): MealCategory {
    println("Which meal do you want to add (${MealCategory.BREAKFAST.label}, ${MealCategory.LUNCH.label}, ${MealCategory.DINNER.label})?")
    val category: MealCategory = parseMealCategoryInput()
    return category
}

fun getMealName(): String {
    println("Input the meal's name:")
    while(true) {
        val mealName = readln()
        if (!mealName.isEmpty() && mealName.matches("^[A-Za-z ]*$".toRegex())) {
            return mealName
        } else {
            println("Wrong format. Use letters only!")
        }
    }
}

fun getInputIngredients(): List<String> {
    var ingredients = listOf<String>()
    var ingredientsIsValid = false
    while (!ingredientsIsValid) {
        println("Input the ingredients:")
        ingredients = readln().split(",").map { it.trim() }
        if (ingredients.any{ it.isEmpty() || !it.matches(Regex("^[a-zA-Z ]+$")) }) {
            println("Wrong format. Use letters only!")
        } else {
            ingredientsIsValid = true
        }
    }
    return ingredients
}

fun addMeal() {
    val category = getMealCategory()
    val mealName = getMealName()
    val ingredients = getInputIngredients()
    val meal = insertMealAndIngredients(category, mealName, ingredients)
    println("The meal has been added!")
}

fun showMeals() {
    println("Which category do you want to print (${MealCategory.BREAKFAST.label}, ${MealCategory.LUNCH.label}, ${MealCategory.DINNER.label})?")
    val category: MealCategory = parseMealCategoryInput()
    val meals = getMeals(category)
    if (meals.isEmpty()) {
        println("No meals found.")
        return
    }
    println("Category: ${category.label}")
    for(meal in meals) {
        println()
        println(meal)
    }
}

fun getMealOption(category: String, dayOfWeek: DayOfWeek): String {
    println("Choose the $category for ${dayOfWeek.label} from the list above:")
    while(true) {
        val name = readln()
        val mealId = getMealIdByNameAndCategory(name, category)
        if (mealId == null) {
            println("This meal doesn’t exist. Choose a meal from the list above.")
        } else {
            return name
        }
    }
}

fun showAvailableMeals() {
    for (dayOfWeek in DayOfWeek.entries) {
        println(dayOfWeek.label)
        val meals = mutableListOf<Meal>()
        for (category in MealCategory.entries) {
            val categoryMeals = getMeals(category)
            categoryMeals.sortedBy { it.name }.forEach { println(it.name) }
            val option: String = getMealOption(category.label, dayOfWeek)
            val mealId = categoryMeals.filter { it.name == option }.first().mealId
            meals.add(Meal(category, option, mealId = mealId))
        }
        insertPlanForDay(dayOfWeek, meals)
    }
    showMealPlan()
}

fun savePlan() {
    val plans = getWeeklyPlan()
    if (plans.isEmpty()) {
        println("Unable to save. Plan your meals first.")
        return
    }
    println("Input a filename:")
    val filename = readln()
    val shoppingList = mutableMapOf<String, Int>()
    for (plan in plans) {
        val ingredients = getMealIngredients(plan.mealId!!)
        for (ingredient in ingredients) {
            if (shoppingList.containsKey(ingredient)) {
                shoppingList.compute(ingredient) { _, value -> value?.plus(1) }
            } else {
                shoppingList[ingredient] = 1
            }
        }
    }
    val outputFile = File(filename)
    if (outputFile.exists()) {
        outputFile.delete()
    }

    for (shoppingItem in shoppingList.entries) {
        val line = "${ if (shoppingItem.value > 1) shoppingItem.key + " x${shoppingItem.value}" else shoppingItem.key }\n"
        outputFile.appendText(line)
    }
    println("Saved!")
}

fun main() {
    createDatabase()

    var input = ""
    while (input != MenuAction.EXIT.label) {
        input = getAction()
        when (input) {
            MenuAction.ADD.label -> addMeal()
            MenuAction.SHOW.label -> showMeals()
            MenuAction.SAVE.label -> savePlan()
            MenuAction.PLAN.label -> showAvailableMeals()
        }
    }
    println("Bye!")
}
