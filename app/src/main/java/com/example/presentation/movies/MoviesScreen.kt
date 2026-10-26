package com.example.presentation.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.MediaCache
import com.example.domain.model.MediaItem
import com.example.domain.repository.MovieRepository
import com.example.ui.components.TvButton
import com.example.ui.components.TvErrorView
import com.example.ui.components.TvLoadingIndicator
import com.example.ui.components.TvMediaCard
import com.example.ui.components.TvTopBar
import com.example.ui.theme.MunowatchBackground
import com.example.ui.theme.MunowatchGreen
import com.example.ui.theme.MunowatchGreenLight
import com.example.ui.theme.MunowatchTextSecondary
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.domain.model.CategoryItem
import com.example.domain.repository.MunowatchRepository
import com.example.domain.repository.SearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MovieSortMode {
    LATEST,
    RANDOM,
    OLD_MOVIES,
    @Deprecated("Replaced with OLD_MOVIES")
    BY_YEAR
}

data class MoviesUiState(
    val isLoading: Boolean = true,
    val isFilterFree: Boolean = false,
    val movies: List<MediaItem> = emptyList(),
    val errorMessage: String? = null,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val sortMode: MovieSortMode = MovieSortMode.LATEST,
    val selectedYear: String? = null,
    val availableYears: List<String> = emptyList(),
    val isYearSelectorOpen: Boolean = false,
    val selectedVj: String? = null,
    val availableVjs: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val availableCategories: List<String> = emptyList()
)

class MoviesViewModel(
    private val movieRepository: MovieRepository,
    private val searchRepository: SearchRepository? = null,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val munowatchRepository: MunowatchRepository? = null
) : ViewModel() {

    private val initialCached = MediaCache.getPopularMovies().filter { isMovieItem(it) }
    private val rawMovies: MutableList<MediaItem> = java.util.Collections.synchronizedList(initialCached.toMutableList())

    private val _uiState = MutableStateFlow(
        MoviesUiState(
            isLoading = initialCached.isEmpty(),
            movies = initialCached,
            canLoadMore = true,
            availableYears = computeAvailableYears(initialCached),
            availableVjs = computeAvailableVjs(initialCached),
            availableCategories = computeAvailableCategories(initialCached)
        )
    )
    val uiState = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var yearFetchJob: Job? = null
    private var vjFetchJob: Job? = null
    private var reshuffleJob: Job? = null
    private var filterFetchJob: Job? = null
    private var oldMoviesJob: Job? = null
    private var oldMoviesBatchIndex: Int = 0
    private var filteredQueryPage: Int = 1
    private var lastFetchId: String? = null
    private var currentPage: Int = 1
    private var yearBatchIndex: Int = 0
    private var reshuffleBatchIndex: Int = 0
    private var categoryBatchPage: Int = 1
    private var vjBatchPage: Int = 1
    private var consecutiveEmptyLoads: Int = 0
    private var isLoadingMoreActive: Boolean = false
    private var yearCatalogPage: Int = 0
    private var yearCatalogEmptyStreak: Int = 0

    private var loadedGenreCategories: List<CategoryItem> = emptyList()
    private var loadedVjCategories: List<CategoryItem> = emptyList()

    var scrollIndex: Int = 0
    var scrollOffset: Int = 0
    var lastOpenedMovieId: String? = null

    init {
        loadMovies(isFree = false, forceReload = false)
        loadBrowseCategories()
    }

    private fun loadBrowseCategories() {
        if (munowatchRepository == null) return
        viewModelScope.launch(ioDispatcher) {
            try {
                val res = munowatchRepository.getCategories()
                res.onSuccess { (genres, vjs) ->
                    loadedGenreCategories = genres
                    loadedVjCategories = vjs
                    val dynamicGenres = genres.map { it.name.trim() }.filter { it.isNotBlank() }
                    val dynamicVjs = vjs.map { it.name.trim() }.filter { it.isNotBlank() }
                    val mergedCategories = (listOf("All Categories") + dynamicGenres + DEFAULT_CATEGORIES.drop(1)).distinct()
                    val mergedVjs = (DEFAULT_VJS + dynamicVjs).distinct()
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            availableCategories = mergedCategories,
                            availableVjs = mergedVjs
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    companion object {
        val YEAR_DISCOVERY_QUERIES: Map<String, List<String>> = mapOf(
            "2026" to listOf(
                "2026", "The Love Hypothesis", "One Night Only", "Runner", "Tarung", "Ice Cream Man",
                "Kattalan", "Hunting Grounds", "No Turning Back", "The Fix", "They Call Me Trinity", "Pakka"
            ),
            "2025" to listOf(
                "2025", "Captain America", "Superman", "Thunderbolts", "Fantastic Four", "Jurassic World",
                "Blade", "Tron", "Avatar", "Mission Impossible", "Predator", "Zootopia", "Minecraft",
                "Dragon", "Karate Kid", "Michael", "Snow White", "Ballerina", "M3GAN"
            ),
            "2024" to listOf(
                "2024", "Deadpool", "Dune", "Gladiator", "Furiosa", "Civil War", "Kingdom", "Alien",
                "Inside Out", "Venom", "Joker", "Kung Fu Panda", "Beetlejuice", "Godzilla", "Quiet Place",
                "Red One", "Trap", "Longlegs", "Bad Boys", "Fall Guy", "Twisters", "Monkey Man",
                "Madame Web", "Borderlands", "Challengers", "Argylle", "Abigail", "Smile", "Crow",
                "Despicable Me", "Land of Bad", "Road House", "Blink Twice", "Wolfs", "Substance"
            ),
            "2023" to listOf(
                "2023", "John Wick", "Creed", "Meg", "Equalizer", "Fast", "Spider-Man", "Guardians",
                "Transformers", "Flash", "Indiana Jones", "Wonka", "Aquaman", "Saw", "Nun", "Oppenheimer",
                "Barbie", "Ant-Man", "Blue Beetle", "Shazam", "Dungeons", "Expendables", "Hunger Games",
                "Gran Turismo", "Creator", "Killers", "Napoleon", "Sound of Freedom", "Five Nights",
                "Mission Impossible", "Ghosted", "Heart of Stone", "Extraction", "Rebel Moon", "Cocaine Bear"
            ),
            "2022" to listOf(
                "2022", "Medieval", "Batman", "Avatar", "Top Gun", "Doctor Strange", "Thor", "Black Panther",
                "Uncharted", "Bullet Train", "Elvis", "Smile", "Morbius", "Northman", "Jurassic World",
                "Black Adam", "Sonic", "Lost City", "Nope", "Ambulance", "Everything Everywhere", "Prey",
                "Men", "Bullet", "Halloween Ends", "Gray Man", "Day Shift", "Beast", "All Quiet"
            ),
            "2021" to listOf(
                "2021", "Spider-Man", "No Way Home", "Dune", "No Time to Die", "Venom", "Shang-Chi",
                "Eternals", "Black Widow", "F9", "Fast & Furious", "Free Guy", "Godzilla vs Kong",
                "Suicide Squad", "Quiet Place", "Matrix", "Cruella", "Ghostbusters", "Jungle Cruise",
                "Halloween Kills", "Mortal Kombat", "Red Notice", "Nobody", "Army of the Dead", "Wrath of Man"
            ),
            "2020" to listOf(
                "2020", "Tenet", "Bad Boys", "Sonic", "Birds of Prey", "Bloodshot", "Mulan", "Wonder Woman",
                "Invisible Man", "Extraction", "Old Guard", "Enola Holmes", "Project Power", "Greenland",
                "Monster Hunter", "Fatman", "Vanguard", "The Gentlemen", "Dolittle", "Underwater"
            ),
            "2019" to listOf(
                "2019", "Avengers", "Endgame", "Spider-Man", "Far From Home", "Captain Marvel", "Joker",
                "Star Wars", "Toy Story", "Aladdin", "Lion King", "John Wick", "Hobbs & Shaw", "Jumanji",
                "Terminator", "Gemini Man", "Rambo", "Alita", "Shazam", "Godzilla", "It Chapter Two",
                "Parasite", "Knives Out", "Us", "Glass", "Hellboy", "Angel Has Fallen", "6 Underground"
            ),
            "2018" to listOf(
                "2018", "Black Panther", "Infinity War", "Deadpool", "Jurassic World", "Incredibles",
                "Aquaman", "Venom", "Mission Impossible", "Ant-Man", "Ready Player One", "Solo",
                "A Quiet Place", "Halloween", "Bumblebee", "Rampage", "Meg", "Equalizer", "Predator",
                "Tomb Raider", "Sicario", "First Man", "Creed", "Bird Box", "Maze Runner", "Red Sparrow"
            ),
            "2017" to listOf(
                "2017", "Star Wars", "Last Jedi", "Beauty and the Beast", "Fate of the Furious", "Thor",
                "Ragnarok", "Wonder Woman", "Spider-Man", "Homecoming", "Wolf Warrior", "Guardians", "It",
                "Logan", "Justice League", "Coco", "Pirates of the Caribbean", "Dunkirk", "War for the Planet",
                "Transformers", "Kong", "Skull Island", "Blade Runner", "Get Out", "Baby Driver", "John Wick",
                "Kingsman", "Alien Covenant", "Atomic Blonde", "Hitman's Bodyguard", "Great Wall"
            ),
            "2016" to listOf(
                "2016", "Captain America", "Civil War", "Rogue One", "Finding Dory", "Zootopia", "Jungle Book",
                "Secret Life of Pets", "Batman v Superman", "Suicide Squad", "Deadpool", "Doctor Strange",
                "Fantastic Beasts", "Moana", "Star Trek", "Jason Bourne", "X-Men", "Tarzan", "Warcraft",
                "Magnificent Seven", "Hacksaw Ridge", "Now You See Me", "London Has Fallen", "13 Hours"
            ),
            "2015" to listOf(
                "2015", "Force Awakens", "Jurassic World", "Furious 7", "Age of Ultron", "Minions",
                "Spectre", "Inside Out", "Mission Impossible", "Hunger Games", "Martian", "Fifty Shades",
                "Ant-Man", "Revenant", "Cinderella", "Mad Max", "Fury Road", "Terminator Genisys",
                "Sicario", "Creed", "San Andreas", "Everest", "Maze Runner", "Hitman"
            ),
            "2014" to listOf(
                "2014", "Interstellar", "Guardians", "Captain America", "Winter Soldier", "Edge of Tomorrow",
                "John Wick", "Dawn of the Planet", "Gone Girl", "Whiplash", "Nightcrawler", "Equalizer", "Fury", "Godzilla"
            ),
            "2013" to listOf(
                "2013", "Iron Man", "Gravity", "Man of Steel", "Frozen", "Fast & Furious", "Hunger Games",
                "Wolf of Wall Street", "Pacific Rim", "World War Z", "Now You See Me", "Prisoners"
            ),
            "2012" to listOf(
                "2012", "Avengers", "Dark Knight Rises", "Skyfall", "Django", "Hobbit", "Life of Pi",
                "Spider-Man", "Looper", "Ted", "Jack Reacher"
            ),
            "2011" to listOf(
                "2011", "Harry Potter", "Transformers", "Fast Five", "Mission Impossible", "Thor",
                "Captain America", "Drive", "Real Steel", "Planet of the Apes"
            ),
            "2010" to listOf(
                "2010", "Inception", "Shutter Island", "Iron Man 2", "Toy Story", "Social Network",
                "Black Swan", "The Town", "Book of Eli", "Salt", "Kick-Ass"
            ),
            "2008" to listOf(
                "2008", "Dark Knight", "Iron Man", "Slumdog", "Tropic Thunder", "Quantum of Solace",
                "Hancock", "Kung Fu Panda", "Taken"
            ),
            "2005" to listOf(
                "2005", "Batman Begins", "King Kong", "War of the Worlds", "Sin City", "Mr and Mrs Smith",
                "Narnia", "Cinderella Man"
            ),
            "2000" to listOf(
                "2000", "Gladiator", "Cast Away", "Mission Impossible", "X-Men", "Snatch", "Crouching Tiger"
            ),
            "1999" to listOf(
                "1999", "Matrix", "Fight Club", "Sixth Sense", "Green Mile", "The Mummy", "Phantom Menace"
            ),
            "1994" to listOf(
                "1994", "Pulp Fiction", "Shawshank", "Forrest Gump", "Lion King", "Speed", "True Lies"
            )
        )

        val CURATED_OLD_QUERIES = listOf(
            "Gladiator", "Matrix", "Fight Club", "Pulp Fiction", "Shawshank", "Forrest Gump", "Speed", "True Lies",
            "The Mummy", "Cast Away", "Batman Begins", "King Kong", "Sin City", "Dark Knight", "Iron Man", "Taken",
            "Inception", "Shutter Island", "Fast Five", "Troy", "Rambo", "Terminator", "Die Hard", "Predator",
            "Alien", "Braveheart", "Blood Diamond", "Crouching Tiger", "Ip Man", "Apocalypto", "RoboCop", "Transporter",
            "Skyfall", "Django", "Avengers", "World War Z", "Interstellar", "John Wick", "Mad Max", "Sicario",
            "Creed", "Logan", "Dunkirk", "Blade Runner", "Equalizer", "Fury", "Expendables", "Jason Bourne",
            "Titanic", "Lord of the Rings", "Pirates of the Caribbean", "Spider-Man 2002", "Bad Boys 1995"
        )

        val DEFAULT_CATEGORIES = listOf(
            "All Categories",
            "Action",
            "Drama",
            "Comedy",
            "Horror",
            "Love Story",
            "Adventure",
            "Sci-Fi",
            "Thriller",
            "Crime",
            "Animation",
            "Family",
            "Mystery",
            "Fantasy",
            "Martial Arts"
        )

        val CATEGORY_DISCOVERY_QUERIES: Map<String, List<String>> = mapOf(
            "Action" to listOf(
                "Action", "John Wick", "Fast", "Mission Impossible", "Extraction", "Equalizer", "Expendables",
                "Bad Boys", "Gladiator", "Jason Bourne", "Deadpool", "Furious", "Top Gun", "Wrath of Man",
                "Nobody", "Transporter", "Taken", "Speed", "Terminator", "Rambo", "Beekeeper", "Rebel Ridge",
                "Twisters", "Civil War", "Bullet Train", "Avengers", "Transformers", "Mad Max", "Sicario",
                "Den of Thieves", "Boy Kills World", "Monkey Man", "Land of Bad", "Die Hard", "Predator"
            ),
            "Drama" to listOf(
                "Drama", "Oppenheimer", "Allied", "Doing Life", "Arafta", "Greenland", "Whale", "First Man",
                "Parasite", "Godfather", "Gladiator", "Titanic", "Revenant", "Cast Away", "Forrest Gump",
                "Prestige", "Beautiful Mind", "Social Network", "Pursuit of Happyness", "Green Mile", "1917",
                "Dunkirk", "Joker", "Ford v Ferrari", "Schindler", "Braveheart", "Shawshank", "Holdovers",
                "Past Lives", "Society of the Snow", "Iron Claw", "Conclave", "Bob Marley", "Sing Sing"
            ),
            "Comedy" to listOf(
                "Comedy", "Free Guy", "Deadpool", "Just Say It", "One Laugh", "Minions", "Jumanji", "Bad Boys",
                "Zootopia", "Ted", "Hangover", "Central Intelligence", "Rush Hour", "Superbad", "Johnny English",
                "Dictator", "Grown Ups", "Spy", "Barbie", "Ghostbusters", "White Chicks", "Step Brothers",
                "Ride Along", "Horrible Bosses", "We're the Millers", "Yes Man", "21 Jump Street"
            ),
            "Horror" to listOf(
                "Horror", "Smile", "Saw", "Nun", "Longlegs", "Quiet Place", "Abigail", "Halloween", "Insidious",
                "Conjuring", "Annabelle", "It", "Sinister", "Evil Dead", "Hereditary", "M3GAN", "Talk to Me",
                "Barbarian", "Scream", "Lights Out", "Exorcist", "Dracula", "Chucky", "Terrifier", "Immaculate",
                "Late Night with the Devil", "Tarot", "Heretic", "Nosferatu", "Hellraiser", "Get Out"
            ),
            "Love Story" to listOf(
                "Love Story", "Romance", "The Love Hypothesis", "You+Me", "Challengers", "Anyone But You",
                "Titanic", "Fault in Our Stars", "Notebook", "Me Before You", "Crazy Rich Asians", "La La Land",
                "After", "Midnight Sun", "Purple Hearts", "Fifty Shades", "Twilight", "Idea of You",
                "It Ends With Us", "Irish Wish", "Upgraded", "Kissing Booth", "Dear John", "About Time"
            ),
            "Adventure" to listOf(
                "Adventure", "Avatar", "Dune", "Jungle Cruise", "Uncharted", "Indiana Jones", "Transformers",
                "Pirates of the Caribbean", "Jumanji", "Kong", "Godzilla", "Jurassic", "Mummy", "Hunger Games",
                "Maze Runner", "Percy Jackson", "Narnia", "Lord of the Rings", "Hobbit", "Harry Potter"
            ),
            "Sci-Fi" to listOf(
                "Sci-Fi", "Alien", "Matrix", "Interstellar", "Inception", "Dune", "Blade Runner", "Avatar",
                "Edge of Tomorrow", "District 9", "Arrival", "Prometheus", "Tenet", "Ready Player One",
                "I Am Legend", "Star Wars", "Kingdom of the Planet of the Apes", "Atlas", "Meg 2",
                "Creator", "Rebel Moon", "Terminator", "Predator", "RoboCop", "Total Recall", "Alita"
            ),
            "Thriller" to listOf(
                "Thriller", "Trap", "Longlegs", "Blink Twice", "Wolfs", "Substance", "Shutter Island",
                "Prisoners", "Gone Girl", "Zodiac", "Se7en", "A Quiet Place", "Bird Box", "Don't Breathe",
                "Split", "Get Out", "Panic Room", "Silence of the Lambs", "Nightcrawler", "Run"
            ),
            "Crime" to listOf(
                "Crime", "MobLand", "The Gentlemen", "Godfather", "Sicario", "Knives Out", "Goodfellas",
                "Scarface", "Departed", "Heat", "Narcos", "Irishman", "Wrath of Man", "Den of Thieves",
                "Training Day", "Casino", "Pulp Fiction", "Snatch", "Baby Driver", "Bikeriders"
            ),
            "Animation" to listOf(
                "Animation", "Inside Out", "Kung Fu Panda", "Spider-Man", "Despicable Me", "Moana", "Zootopia",
                "Shrek", "How to Train Your Dragon", "Incredibles", "Coco", "Toy Story", "Cars", "Frozen",
                "Madagascar", "Wild Robot", "Transformers One", "Puss in Boots", "Minions", "Sing",
                "Super Mario Bros", "Elemental", "Luca", "Soul", "Turning Red", "Encanto"
            ),
            "Family" to listOf(
                "Family", "Wonka", "Sonic", "Minecraft", "Paddington", "Dolittle", "Night at the Museum",
                "Home Alone", "Karate Kid", "Chronicles of Narnia", "Jungle Book", "Aladdin", "Lion King"
            ),
            "Martial Arts" to listOf(
                "Martial Arts", "Kung Fu", "Lion Fist", "Ip Man", "Karate Kid", "Mortal Kombat", "Raid",
                "Ong Bak", "Drunken Master", "Fearless", "Hero", "Crouching Tiger", "Fist of Legend",
                "Shaolin", "Undisputed", "Boy Kills World", "Monkey Man", "Warrior", "Cobra Kai",
                "Enter the Dragon", "Police Story", "Kung Fu Hustle", "Shaolin Soccer"
            ),
            "Sport" to listOf(
                "Sport", "Creed", "Rocky", "Air", "Gran Turismo", "Challengers", "Ferrari", "Ford v Ferrari",
                "Iron Claw", "Moneyball", "Karate Kid", "Southpaw", "Warrior", "King Richard", "Hustle"
            )
        )

        val CATEGORY_KEYWORD_PATTERNS: Map<String, List<String>> = mapOf(
            "Action" to listOf(
                "action", "war", "fight", "battle", "combat", "killer", "assassin", "hitman", "spy", "agent",
                "cop", "police", "swat", "commando", "sniper", "explode", "explosion", "hostage", "heist",
                "mafia", "cartel", "martial", "weapon", "gun", "chase", "shoot", "brawl", "strike", "mission",
                "fury", "soldier", "revenge", "john wick", "wick", "fast", "furious", "mission impossible",
                "extraction", "equalizer", "expendables", "bad boys", "gladiator", "bourne", "deadpool",
                "top gun", "nobody", "transporter", "taken", "speed", "terminator", "rambo", "avengers",
                "transformers", "matrix", "predator", "alien", "wrath of man", "die hard", "mad max",
                "beekeeper", "rebel ridge", "twisters", "godzilla", "kong", "avatar", "ip man",
                "mortal kombat", "bloodsport", "raid", "ong bak", "ninja", "shooter", "bounty", "vengeance",
                "havoc", "bullet train", "civil war", "den of thieves", "sicario", "fallout", "iron man",
                "captain america", "batman", "superman", "spider-man", "thor", "black panther", "aquaman",
                "justice league", "guardians", "monkey man", "furiosa", "land of bad", "boy kills world",
                "air force one", "white house down", "olympus has fallen", "london has fallen", "angel has fallen"
            ),
            "Drama" to listOf(
                "drama", "dramatic", "emotional", "struggle", "life", "court", "trial", "tears", "biopic",
                "justice", "survivor", "dying", "disease", "lawyer", "faith", "prison", "escape", "truth",
                "guilt", "father", "mother", "son", "daughter", "tragedy", "doing life", "oppenheimer",
                "allied", "arafta", "greenland", "whale", "first man", "parasite", "godfather", "titanic",
                "revenant", "cast away", "forrest gump", "prestige", "beautiful mind", "social network",
                "pursuit of happyness", "green mile", "1917", "dunkirk", "joker", "ford v ferrari",
                "schindler", "braveheart", "shawshank", "killers of the flower moon", "maestro", "past lives",
                "society of the snow", "iron claw", "zone of interest", "anatomy of a fall", "holdovers",
                "poor things", "cabrini", "one life", "bob marley", "back to black", "sing sing", "conclave"
            ),
            "Comedy" to listOf(
                "comedy", "comedic", "funny", "humor", "spoof", "hilarious", "parody", "satire", "sitcom",
                "rom-com", "romcom", "goofy", "free guy", "deadpool", "minions", "just say it",
                "one laugh", "jumanji", "bad boys", "zootopia", "ted", "hangover", "central intelligence",
                "rush hour", "superbad", "johnny english", "dictator", "grown ups", "spy", "dumb", "dumber",
                "barbie", "ghostbusters", "mean girls", "fly me to the moon", "beetlejuice", "red one",
                "white chicks", "step brothers", "ride along", "night school", "horrible bosses",
                "we're the millers", "happy gilmore", "liar liar", "mask", "ace ventura", "bruce almighty",
                "yes man", "tropic thunder", "zoolander", "21 jump street", "22 jump street"
            ),
            "Horror" to listOf(
                "horror", "scary", "haunted", "demon", "terror", "creepy", "ghoul", "corpse", "gore", "gruesome",
                "possessed", "possession", "vampire", "zombie", "witchcraft", "slasher", "devil", "satan", "undead",
                "saw x", "saw ii", "saw iii", "saw iv", "saw v", "saw vi", "saw 3d", "jigsaw", "saw",
                "the nun", "nun ii", "nun 2", "nun", "a quiet place", "quiet place", "insidious", "conjuring",
                "the conjuring", "annabelle", "pennywise", "sinister", "evil dead", "hereditary", "m3gan",
                "talk to me", "barbarian", "scream", "lights out", "the exorcist", "exorcist", "exorcism",
                "dracula", "chucky", "child's play", "terrifier", "immaculate", "the first omen", "first omen", "omen",
                "late night with the devil", "tarot", "cuckoo", "speak no evil", "heretic", "nosferatu",
                "hellraiser", "midsommar", "the babadook", "babadook", "get out", "the black phone", "black phone",
                "texas chainsaw", "hills have eyes", "wrong turn", "final destination", "cabin in the woods",
                "pet sematary", "hostel", "ouija", "jeepers creepers", "amityville", "night swim", "smile", "smile 2",
                "in a violent nature", "oddity", "the substance", "paranormal activity", "the ring", "the grudge", "grudge",
                "candyman", "poltergeist", "dead silence", "dark circles", "halloween", "friday the 13th",
                "nightmare on elm street", "blair witch", "underworld", "resident evil", "the mist", "dont breathe",
                "don't breathe", "the descent", "the fog", "darkness falls", "ghost ship", "orphan"
            ),
            "Love Story" to listOf(
                "love story", "romance", "romantic", "dating", "affair", "couple", "marriage", "marry",
                "wedding", "husband", "wife", "girlfriend", "boyfriend", "heart", "passion", "kiss",
                "lover", "challengers", "hypothesis", "love", "you+me", "anyone but you", "fault in our stars",
                "notebook", "me before you", "crazy rich asians", "la la land", "after", "midnight sun",
                "purple hearts", "fifty shades", "twilight", "idea of you", "it ends with us", "family affair",
                "lonely planet", "irish wish", "players", "upgraded", "to all the boys", "kissing booth",
                "dear john", "safe haven", "walk to remember", "about time", "love actually", "pretty woman"
            ),
            "Adventure" to listOf(
                "adventure", "quest", "journey", "explorer", "treasure", "island", "jungle", "lost",
                "temple", "survival", "avatar", "dune", "uncharted", "indiana", "jurassic", "jungle cruise",
                "transformers", "pirates of the caribbean", "kong", "godzilla", "mummy", "tomb raider",
                "hunger games", "maze runner", "divergent", "percy jackson", "narnia", "lord of the rings",
                "hobbit", "harry potter", "fantastic beasts"
            ),
            "Sci-Fi" to listOf(
                "sci-fi", "sci fi", "science fiction", "alien", "aliens", "space", "planet", "spaceship",
                "galaxy", "time travel", "future", "futuristic", "clone", "robot", "ai", "cosmos",
                "matrix", "interstellar", "inception", "dune", "blade runner", "edge of tomorrow",
                "district 9", "arrival", "prometheus", "tenet", "ready player one", "i am legend",
                "star wars", "star trek", "kingdom of the planet of the apes", "atlas", "spaceman",
                "meg 2", "creator", "rebel moon", "terminator", "predator", "robocop", "total recall",
                "fifth element", "minority report", "oblivion", "elysium", "alita", "ex machina"
            ),
            "Thriller" to listOf(
                "thriller", "suspense", "kidnapping", "serial killer", "stalker", "paranoia", "trap",
                "wolfs", "substance", "longlegs", "prisoners", "shutter", "gone girl", "zodiac", "se7en",
                "bird box", "don't breathe", "split", "get out", "panic room"
            ),
            "Crime" to listOf(
                "crime", "criminal", "heist", "robbery", "mafia", "mob", "gangster", "cartel", "underworld",
                "thief", "detective", "undercover", "prison", "jail", "syndicate", "drug", "sicario",
                "godfather", "mobland", "gentlemen", "knives out", "goodfellas", "scarface", "departed",
                "heat", "narcos", "irishman", "training day", "casino", "pulp fiction", "snatch",
                "baby driver", "nightcrawler", "town", "american gangster"
            ),
            "Animation" to listOf(
                "animation", "animated", "anime", "cartoon", "cgi", "pixar", "disney", "dreamworks",
                "kung fu panda", "inside out", "despicable", "moana", "spider-man", "shrek",
                "how to train your dragon", "incredibles", "coco", "toy story", "cars", "frozen",
                "madagascar", "lion king", "wild robot", "transformers one", "puss in boots", "minions",
                "sing", "secret life of pets", "super mario", "elemental", "wish", "luca", "soul",
                "turning red", "encanto", "raya", "big hero 6", "finding nemo", "monsters inc", "up",
                "wall-e", "ratatouille", "brave", "tangled", "wreck-it ralph", "ice age", "rio",
                "megamind", "croods", "hotel transylvania", "boss baby"
            ),
            "Family" to listOf(
                "family", "kids", "children", "wholesome", "sonic", "wonka", "minecraft", "paddington",
                "dolittle", "night at the museum", "home alone", "karate kid", "chronicles of narnia"
            ),
            "Martial Arts" to listOf(
                "martial arts", "kung fu", "kungu fu", "karate", "shaolin", "judo", "ninja", "samurai",
                "wushu", "ip man", "lion fist", "mortal kombat", "raid", "ong bak", "drunken master",
                "fearless", "hero", "crouching tiger", "fist of legend", "undisputed", "boy kills world",
                "monkey man", "warrior", "cobra kai", "enter the dragon", "way of the dragon",
                "legend of the drunken master", "police story", "rumble in the bronx", "tai chi master",
                "iron monkey", "kung fu hustle", "shaolin soccer"
            ),
            "Sport" to listOf(
                "sport", "sports", "boxing", "football", "soccer", "racing", "basketball", "creed", "rocky",
                "air", "gran turismo", "challengers", "ferrari", "ford v ferrari", "iron claw", "moneyball",
                "karate kid", "southpaw", "warrior", "f1"
            )
        )

        val DEFAULT_VJS = listOf(
            "All VJs",
            "VJ Junior",
            "VJ ICEP",
            "VJ Emmy",
            "VJ Jingo",
            "VJ Ulio",
            "VJ Soul",
            "VJ Kevo",
            "VJ Mosco",
            "VJ Mark",
            "VJ Sammy",
            "VJ Ray",
            "VJ Dan",
            "VJ Henrico"
        )

        fun isMovieItem(item: MediaItem): Boolean {
            if (item.isSeries) return false
            val idLower = item.id.lowercase().trim()
            if (idLower.startsWith("series_") || idLower.contains("_ep") || idLower.startsWith("ep_")) return false
            val streamLower = item.streamUrl.lowercase().trim()
            if (streamLower.contains("seriesdetails.php") || streamLower.contains("_ep") || streamLower.contains("ep_")) return false
            val title = item.title
            if (Regex("""(?i)\b(?:Season|Seasons|Series|S\d{1,2}|Episode|Episodes|EPS|Ep\s*\d+)\b""").containsMatchIn(title)) return false
            return true
        }

        val KNOWN_GENRE_IDS: Map<String, Int> = mapOf(
            "action" to 1,
            "horror" to 2,
            "adventure" to 7,
            "love story" to 8,
            "romance" to 15,
            "comedy" to 9,
            "crime" to 12,
            "family" to 13,
            "sci-fi" to 14,
            "sci fi" to 14,
            "kungu fu" to 16,
            "kung fu" to 16,
            "martial arts" to 16,
            "drama" to 17,
            "sport" to 18,
            "thriller" to 19,
            "animation" to 20,
            "korean" to 21,
            "filipino" to 22,
            "indian" to 23,
            "chinese" to 24,
            "ugandan" to 25
        )

        val KNOWN_VJ_IDS: Map<String, Int> = mapOf(
            "vj junior" to 1,
            "junior" to 1,
            "vj emmy" to 2,
            "emmy" to 2,
            "vj ice p" to 9,
            "vj icep" to 9,
            "ice p" to 9,
            "icep" to 9,
            "vj tom" to 4,
            "tom" to 4,
            "vj ulio" to 38,
            "ulio" to 38,
            "vj jingo" to 8,
            "jingo" to 8,
            "vj shao khan" to 5,
            "shao khan" to 5,
            "vj sammy" to 26,
            "sammy" to 26,
            "vj soul" to 57,
            "soul" to 57,
            "vj kevo" to 14,
            "kevo" to 14,
            "vj kevin" to 16,
            "kevin" to 16,
            "vj hd" to 24,
            "hd" to 24,
            "vj dan de" to 25,
            "vj dan dee" to 25,
            "dan de" to 25
        )

        val uploadTimestampCache = java.util.concurrent.ConcurrentHashMap<String, Long>()
        val yearCache = java.util.concurrent.ConcurrentHashMap<String, String>()
        val categoryCache = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap.KeySetView<String, Boolean>>()
        val oldMovieCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        val vjMatchCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        val categoryMatchCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

        fun clearCaches() {
            uploadTimestampCache.clear()
            yearCache.clear()
            categoryCache.clear()
            oldMovieCache.clear()
            vjMatchCache.clear()
            categoryMatchCache.clear()
        }

        fun recordItemCategory(itemId: String, category: String) {
            if (itemId.isBlank() || category.isBlank()) return
            categoryCache.getOrPut(itemId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }
                .add(category.lowercase().trim())
        }

        fun findTimestampMs(text: String): Long? {
            if (text.isBlank()) return null
            // 13 digits epoch millis
            val ms = Regex("""(?:\D|^)(1[5-9]\d{11})(?:\D|$)""").find(text)?.groupValues?.get(1)?.toLongOrNull()
            if (ms != null && ms in 1500000000000L..2100000000000L) {
                return ms
            }
            // 10 digits epoch seconds
            val sec = Regex("""(?:\D|^)(1[5-9]\d{8})(?:\D|$)""").find(text)?.groupValues?.get(1)?.toLongOrNull()
            if (sec != null && sec in 1500000000L..2100000000L) {
                return sec * 1000L
            }
            return null
        }

        fun timestampToYear(timestampMs: Long): String {
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            cal.timeInMillis = timestampMs
            return cal.get(java.util.Calendar.YEAR).toString()
        }

        fun extractUploadTimestamp(item: MediaItem): Long? {
            if (item.id.isNotBlank()) {
                uploadTimestampCache[item.id]?.let { return it }
            }
            val calculated = computeUploadTimestamp(item)
            if (calculated != null && item.id.isNotBlank()) {
                uploadTimestampCache[item.id] = calculated
            }
            return calculated
        }

        private fun computeUploadTimestamp(item: MediaItem): Long? {
            // 1. Check posterUrl
            findTimestampMs(item.posterUrl)?.let { return it }
            // 2. Check streamUrl
            findTimestampMs(item.streamUrl)?.let { return it }
            // 3. Check backdropUrl
            findTimestampMs(item.backdropUrl)?.let { return it }

            // 4. Derive from year field, title or description
            val yStr = if (item.year.isNotBlank()) {
                Regex("""\b(19\d{2}|20\d{2})\b""").find(item.year)?.value
            } else {
                Regex("""\b(19\d{2}|20\d{2})\b""").find(item.title)?.value
                    ?: Regex("""\b(19\d{2}|20\d{2})\b""").find(item.description)?.value
            }
            val yInt = yStr?.toIntOrNull()
            if (yInt != null && yInt in 1980..2030) {
                val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                cal.clear()
                cal.set(java.util.Calendar.YEAR, yInt)
                cal.set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY)
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                val baseMs = cal.timeInMillis
                val idOffset = (item.id.filter { it.isDigit() }.take(6).toLongOrNull() ?: 0L) * 1000L
                return baseMs + idOffset
            }
            return null
        }

        fun extractYear(item: MediaItem): String? {
            if (item.year.length == 4 && item.year.all { it.isDigit() }) {
                return item.year
            }
            if (item.id.isNotBlank()) {
                yearCache[item.id]?.let { return it }
            }
            val calculated = computeYear(item)
            if (calculated != null && item.id.isNotBlank()) {
                yearCache[item.id] = calculated
            }
            return calculated
        }

        private fun computeYear(item: MediaItem): String? {
            if (item.year.isNotBlank()) {
                val y = Regex("""\b(19\d{2}|20\d{2})\b""").find(item.year)?.value
                if (y != null && (y.toIntOrNull() ?: 0) in 1980..2030) return y
            }
            val titleYear = Regex("""\b(19\d{2}|20\d{2})\b""").find(item.title)?.value
            if (titleYear != null && (titleYear.toIntOrNull() ?: 0) in 1980..2030) return titleYear

            val streamYear = Regex("""(?i)[?&]year=(19\d{2}|20\d{2})\b""").find(item.streamUrl)?.groupValues?.get(1)
            if (streamYear != null && (streamYear.toIntOrNull() ?: 0) in 1980..2030) return streamYear

            val descYear = Regex("""\b(19\d{2}|20\d{2})\b""").find(item.description)?.value
            if (descYear != null && (descYear.toIntOrNull() ?: 0) in 1980..2030) return descYear

            // Derive from known curated blockbusters
            val cleanTitle = item.title.lowercase()
            for ((y, titles) in YEAR_DISCOVERY_QUERIES) {
                if (titles.any { t -> t.length >= 4 && !t.all { c -> c.isDigit() } && cleanTitle.contains(t.lowercase()) }) {
                    return y
                }
            }

            // Derive from poster or stream upload timestamp
            val ts = findTimestampMs(item.posterUrl) ?: findTimestampMs(item.backdropUrl) ?: findTimestampMs(item.streamUrl)
            if (ts != null) {
                val y = timestampToYear(ts)
                if ((y.toIntOrNull() ?: 0) in 1990..2030) {
                    return y
                }
            }

            return null
        }

        fun matchesYear(item: MediaItem, targetYear: String): Boolean {
            if (item.year == targetYear) return true
            val ey = extractYear(item)
            if (!ey.isNullOrBlank()) {
                return ey == targetYear
            }
            val titleYear = Regex("""\b(19\d{2}|20\d{2})\b""").find(item.title)?.value
            if (titleYear != null) {
                return titleYear == targetYear
            }
            if (item.streamUrl.contains("year=$targetYear")) {
                return true
            }
            val cleanTitle = item.title.lowercase()
            val yearTitles = YEAR_DISCOVERY_QUERIES[targetYear] ?: emptyList()
            return yearTitles.any { t -> t.length >= 4 && !t.all { c -> c.isDigit() } && cleanTitle.contains(t.lowercase()) }
        }

        val ADDITIONAL_CLASSIC_TITLES = listOf(
            "matrix", "gladiator", "terminator", "rambo", "predator", "alien", "die hard", "speed", "robocop",
            "titanic", "ip man", "ong bak", "drunken master", "rush hour", "bad boys", "fast and furious", "fast five",
            "tokyo drift", "john wick", "equalizer", "taken", "transporter", "crank", "bourne", "top gun",
            "con air", "the rock", "face off", "armageddon", "deep impact", "independence day", "men in black",
            "jurassic park", "king kong", "godzilla", "pacific rim", "blade", "underworld", "resident evil", "van helsing",
            "constantine", "ghost rider", "hellboy", "punisher", "spider-man", "batman", "dark knight", "iron man",
            "thor", "captain america", "avengers", "hulk", "wolverine", "x-men", "fantastic four", "transformers",
            "alita", "ready player one", "interstellar", "gravity", "the martian", "oblivion", "edge of tomorrow",
            "elysium", "district 9", "chappie", "i am legend", "world war z", "train to busan", "zombieland", "mad max",
            "sicario", "creed", "logan", "dunkirk", "1917", "american sniper", "lone survivor", "zero dark thirty",
            "shooter", "black hawk down", "saving private ryan", "braveheart", "apocalypto", "troy", "300",
            "django", "inglourious", "kill bill", "pulp fiction", "goodfellas", "scarface", "godfather", "heat",
            "shawshank", "green mile", "forrest gump", "cast away", "se7en", "fight club", "shutter island",
            "prestige", "harry potter", "lord of the rings", "hobbit", "narnia", "hunger games", "maze runner",
            "divergent", "twilight", "lucy", "salt", "atomic blonde", "colombiana", "blood diamond", "apocalypse",
            "guardians of the tomb", "guardians of the night", "spy time", "samurai cop", "blood and bone", "undisputed",
            "boyka", "first blood", "demolition man", "judge dredd", "universal soldier", "blood debt", "lethal weapon"
        )

        val OLD_BLOCKBUSTERS_SET: Set<String> by lazy {
            val titles = mutableSetOf<String>()
            YEAR_DISCOVERY_QUERIES.forEach { (yearStr, list) ->
                val y = yearStr.toIntOrNull() ?: 9999
                if (y <= 2025) {
                    list.filter { it.length >= 4 && !it.all { c -> c.isDigit() } }.forEach {
                        titles.add(it.lowercase())
                    }
                }
            }
            ADDITIONAL_CLASSIC_TITLES.forEach { titles.add(it.lowercase()) }
            titles
        }

        fun isOldMovie(item: MediaItem): Boolean {
            if (item.id.isNotBlank()) {
                oldMovieCache[item.id]?.let { return it }
            }
            val res = computeIsOldMovie(item)
            if (item.id.isNotBlank()) {
                oldMovieCache[item.id] = res
            }
            return res
        }

        private fun computeIsOldMovie(item: MediaItem): Boolean {
            // 1. Check direct year metadata or cached year in MediaCache
            val effectiveYear = item.year.takeIf { it.isNotBlank() }
                ?: MediaCache.get(item.id)?.year?.takeIf { it.isNotBlank() }
                ?: MediaCache.getMovieDetail(item.id)?.year?.takeIf { it.isNotBlank() }
                ?: extractYear(item)

            if (!effectiveYear.isNullOrBlank()) {
                val y = effectiveYear.filter { it.isDigit() }.take(4).toIntOrNull()
                if (y != null && y in 1950..2020) return true
                if (y != null && y > 2020) return false
            }
            // 3. Check year in streamUrl
            val streamYear = Regex("""(?i)[?&]year=(19\d{2}|20\d{2})\b""").find(item.streamUrl)?.groupValues?.get(1)?.toIntOrNull()
            if (streamYear != null) {
                if (streamYear in 1950..2020) return true
                if (streamYear > 2020) return false
            }
            // 4. Check year in title
            val titleYear = Regex("""\b(19\d{2}|20\d{2})\b""").find(item.title)?.value?.toIntOrNull()
            if (titleYear != null) {
                if (titleYear in 1950..2020) return true
                if (titleYear > 2020) return false
            }
            // 5. Check classic blockbuster title match
            val cleanTitle = item.title.lowercase()
            if (OLD_BLOCKBUSTERS_SET.any { cleanTitle.contains(it) }) {
                return true
            }
            // 6. Check upload or poster timestamp (<= Jan 1, 2021)
            val uploadTs = extractUploadTimestamp(item)
            if (uploadTs != null && uploadTs in 946684800000L..1609459200000L) {
                return true
            }
            return false
        }

        val KNOWN_OTHER_VJS: List<String> = listOf(
            "junior", "ice p", "icep", "emmy", "emmie", "jingo", "ulio", "soul",
            "kevo", "kevin", "mosco", "mark", "sammy", "ray", "dan", "dan de", "dan dee",
            "henrico", "bonny", "cb", "heavy q", "heavyq", "tom", "shao khan", "hd",
            "martin k", "aron", "freddie", "hashim", "ivo", "isma k", "kamran",
            "ben", "smart", "coke", "moon", "cox", "shakie", "maserati", "henny",
            "son", "smash", "joker", "kas", "bobby", "florence", "jamil"
        )

        private val OTHER_VJ_TITLE_REGEX_MAP: Map<String, Regex> by lazy {
            KNOWN_OTHER_VJS.associateWith { otherVj ->
                Regex("""\b(?:vj\s*${Regex.escape(otherVj)}|\[\s*(?:vj\s*)?${Regex.escape(otherVj)}\s*\]|\(\s*(?:vj\s*)?${Regex.escape(otherVj)}\s*\)|translated\s+by\s+${Regex.escape(otherVj)}|luganda\s+by\s+${Regex.escape(otherVj)})\b""", RegexOption.IGNORE_CASE)
            }
        }

        private val OTHER_VJ_DESC_REGEX_MAP: Map<String, Regex> by lazy {
            KNOWN_OTHER_VJS.associateWith { otherVj ->
                Regex("""\b(?:vj\s*${Regex.escape(otherVj)}|translated\s+by\s+${Regex.escape(otherVj)})\b""", RegexOption.IGNORE_CASE)
            }
        }

        private val TARGET_VJ_TITLE_REGEX_CACHE = java.util.concurrent.ConcurrentHashMap<String, Regex>()
        private val TARGET_VJ_DESC_REGEX_CACHE = java.util.concurrent.ConcurrentHashMap<String, Regex>()

        fun getTargetVjTitleRegex(cleanTarget: String): Regex {
            return TARGET_VJ_TITLE_REGEX_CACHE.computeIfAbsent(cleanTarget) { target ->
                Regex("""\b(?:vj\s*${Regex.escape(target)}|\[\s*(?:vj\s*)?${Regex.escape(target)}\s*\]|\(\s*(?:vj\s*)?${Regex.escape(target)}\s*\)|translated\s+by\s+${Regex.escape(target)}|luganda\s+by\s+${Regex.escape(target)}|${Regex.escape(target)}\s*vj)\b""", RegexOption.IGNORE_CASE)
            }
        }

        fun getTargetVjDescRegex(cleanTarget: String): Regex {
            return TARGET_VJ_DESC_REGEX_CACHE.computeIfAbsent(cleanTarget) { target ->
                Regex("""\b(?:vj\s*${Regex.escape(target)}|translated\s+by\s+${Regex.escape(target)}|luganda\s+by\s+${Regex.escape(target)}|${Regex.escape(target)}\s*vj)\b""", RegexOption.IGNORE_CASE)
            }
        }

        fun cleanVjString(s: String): String {
            return s.lowercase()
                .replace("vj", "")
                .replace(".", "")
                .replace("-", " ")
                .replace("_", " ")
                .replace("[", "")
                .replace("]", "")
                .replace("(", "")
                .replace(")", "")
                .trim()
        }

        fun matchesVj(item: MediaItem, targetVj: String?): Boolean {
            if (targetVj.isNullOrBlank() || targetVj.equals("All VJs", ignoreCase = true)) return true
            val cleanTarget = cleanVjString(targetVj)
            if (cleanTarget.isBlank()) return true

            val cacheKey = if (item.id.isNotBlank()) "${item.id}_$cleanTarget" else null
            if (cacheKey != null) {
                vjMatchCache[cacheKey]?.let { return it }
            }

            val result = computeMatchesVj(item, cleanTarget)
            if (cacheKey != null) {
                vjMatchCache[cacheKey] = result
            }
            return result
        }

        private fun computeMatchesVj(item: MediaItem, cleanTarget: String): Boolean {
            // 1. Authoritative check on item.vj if present
            val rawItemVj = item.vj.trim()
            if (rawItemVj.isNotBlank() && rawItemVj != "-" && !rawItemVj.equals("null", ignoreCase = true)) {
                val itemVjClean = cleanVjString(rawItemVj)
                if (itemVjClean.isNotBlank()) {
                    if (itemVjClean == cleanTarget) return true
                    if ((cleanTarget == "ice p" || cleanTarget == "icep") && (itemVjClean == "ice p" || itemVjClean == "icep")) return true
                    if ((cleanTarget == "emmy" || cleanTarget == "emmie") && (itemVjClean == "emmy" || itemVjClean == "emmie")) return true
                    if ((cleanTarget == "dan de" || cleanTarget == "dan dee") && (itemVjClean == "dan de" || itemVjClean == "dan dee")) return true
                    if ((cleanTarget == "heavy q" || cleanTarget == "heavyq") && (itemVjClean == "heavy q" || itemVjClean == "heavyq")) return true

                    // If item.vj is explicitly set to a different VJ, NEVER treat as matching!
                    return false
                }
            }

            // 2. If item.vj is blank, inspect title and streamUrl / description
            val titleLower = item.title.lowercase()

            // 2a. Reject if title explicitly mentions a DIFFERENT known VJ
            for (otherVj in KNOWN_OTHER_VJS) {
                if (otherVj == cleanTarget) continue
                if (cleanTarget in listOf("ice p", "icep") && otherVj in listOf("ice p", "icep")) continue
                if (cleanTarget in listOf("emmy", "emmie") && otherVj in listOf("emmy", "emmie")) continue
                if (cleanTarget in listOf("dan de", "dan dee") && otherVj in listOf("dan de", "dan dee")) continue
                if (cleanTarget in listOf("heavy q", "heavyq") && otherVj in listOf("heavy q", "heavyq")) continue

                if (!titleLower.contains(otherVj)) continue

                val otherRegex = OTHER_VJ_TITLE_REGEX_MAP[otherVj]
                if (otherRegex != null && otherRegex.containsMatchIn(titleLower)) {
                    return false
                }
            }

            // 2b. Match if title credits target VJ
            if (titleLower.contains(cleanTarget)) {
                val targetRegexInTitle = getTargetVjTitleRegex(cleanTarget)
                if (targetRegexInTitle.containsMatchIn(titleLower)) {
                    return true
                }
            }

            // 2c. Check streamUrl for vj parameter: e.g. vj=junior or vj=1
            val streamLower = item.streamUrl.lowercase()
            if (streamLower.contains("vj=${cleanTarget.replace(" ", "")}") || streamLower.contains("vj=${cleanTarget.replace(" ", "+")}")) {
                return true
            }

            // 2d. Strict check in description: requires explicit VJ credit phrasing, NOT bare word
            val descLower = item.description.lowercase()
            if (descLower.isNotBlank() && !descLower.equals("null", ignoreCase = true)) {
                for (otherVj in KNOWN_OTHER_VJS) {
                    if (otherVj == cleanTarget) continue
                    if (!descLower.contains(otherVj)) continue

                    val otherRegex = OTHER_VJ_DESC_REGEX_MAP[otherVj]
                    if (otherRegex != null && otherRegex.containsMatchIn(descLower)) {
                        return false
                    }
                }
                if (descLower.contains(cleanTarget)) {
                    val targetRegexInDesc = getTargetVjDescRegex(cleanTarget)
                    if (targetRegexInDesc.containsMatchIn(descLower)) {
                        return true
                    }
                }
            }

            return false
        }

        private val HORROR_INDICATORS: Set<String> = setOf(
            "horror", "slasher", "haunted", "possession", "possessed", "demon", "exorcist",
            "nightmare", "halloween", "massacre", "evil dead", "creepy", "paranormal",
            "witch", "undead", "zombie", "terrifier", "sinister", "annabelle",
            "insidious", "conjuring", "blood", "corpse", "gore", "gruesome", "occult",
            "hereditary", "chucky", "pennywise", "immaculate", "longlegs", "first omen",
            "it cuts deep", "cuts deep", "dark circles", "hokum", "v for vengeance",
            "mutilat", "slay", "devil", "hellraiser", "babadook", "midsommar",
            "ouija", "candyman", "purge", "jeepers creepers", "silent hill", "amityville",
            "texas chainsaw", "hills have eyes", "wrong turn", "final destination",
            "cabin in the woods", "shutter", "the ring", "pet sematary", "hostel",
            "abigail", "tarot", "exorcism", "quiet place", "black phone", "talk to me",
            "bodies bodies bodies", "barbarian", "maxxxine", "cuckoo", "speak no evil",
            "heretic", "nosferatu", "night swim", "imaginary", "in a violent nature",
            "oddity", "substance", "alien: romulus", "salem's lot", "smile 2"
        )

        private val COMEDY_INDICATORS: Set<String> = setOf(
            "stand-up", "sitcom", "parody", "rom-com", "romcom", "romantic comedy", "family comedy"
        )

        private val COMEDY_KEYWORDS_REGEX: Regex = Regex(
            """\b(comedy|comedic|funny|humor|parody|satire|sitcom|stand-up)\b""",
            RegexOption.IGNORE_CASE
        )

        private val COMBAT_ACTION_REGEX: Regex = Regex(
            """\b(rogue|mercenary|mercenaries|commando|commandos|sniper|snipers|special forces|warfare|counter-terror|cartel|hitman|assassin|assassins|hostage|hostages|the outpost|army of one|the perfect weapon|disturbing the peace|welcome to sudden death|the machinery|safeguard)\b""",
            RegexOption.IGNORE_CASE
        )

        private val KNOWN_GENRES: List<String> = listOf(
            "horror", "comedy", "action", "romance", "love story",
            "drama", "animation", "sci-fi", "thriller", "documentary",
            "adventure", "crime", "family", "mystery", "fantasy", "martial arts", "kung fu"
        )

        private val CATEGORY_KEYWORD_REGEX_MAP: Map<String, List<Pair<String, Regex?>>> by lazy {
            CATEGORY_KEYWORD_PATTERNS.mapValues { (_, patterns) ->
                patterns.filter { it.length >= 3 && it != "it" }.map { p ->
                    if (p.contains(" ")) {
                        Pair(p.lowercase(), null)
                    } else {
                        Pair(p.lowercase(), Regex("""\b${Regex.escape(p)}\b""", RegexOption.IGNORE_CASE))
                    }
                }
            }
        }

        val EXPLICIT_GENRE_PATTERNS: Map<String, Regex> by lazy {
            mapOf(
                "horror" to Regex("""\b(horror film|horror movie|supernatural horror|psychological horror|slasher film|demonic possession|haunted house)\b""", RegexOption.IGNORE_CASE),
                "comedy" to Regex("""\b(comedy film|comedy movie|romantic comedy|slapstick comedy|stand-up comedy)\b""", RegexOption.IGNORE_CASE),
                "action" to Regex("""\b(action film|action movie|martial arts action|action thriller)\b""", RegexOption.IGNORE_CASE),
                "love story" to Regex("""\b(romance film|romantic movie|love story|romantic drama)\b""", RegexOption.IGNORE_CASE),
                "romance" to Regex("""\b(romance film|romantic movie|love story|romantic drama)\b""", RegexOption.IGNORE_CASE),
                "sci-fi" to Regex("""\b(science fiction|sci-fi film|sci-fi movie|space exploration)\b""", RegexOption.IGNORE_CASE),
                "sci fi" to Regex("""\b(science fiction|sci-fi film|sci-fi movie|space exploration)\b""", RegexOption.IGNORE_CASE),
                "thriller" to Regex("""\b(thriller film|thriller movie|psychological thriller|crime thriller)\b""", RegexOption.IGNORE_CASE),
                "animation" to Regex("""\b(animated film|animated movie|anime film|cartoon series)\b""", RegexOption.IGNORE_CASE),
                "drama" to Regex("""\b(drama film|drama movie|historical drama|biographical drama)\b""", RegexOption.IGNORE_CASE),
                "crime" to Regex("""\b(crime film|crime movie|gangster film|organized crime)\b""", RegexOption.IGNORE_CASE),
                "adventure" to Regex("""\b(adventure film|adventure movie|epic adventure)\b""", RegexOption.IGNORE_CASE)
            )
        }

        fun matchesCategory(item: MediaItem, targetCategory: String?): Boolean {
            if (targetCategory.isNullOrBlank() || targetCategory.equals("All Categories", ignoreCase = true) || targetCategory.equals("All Genres", ignoreCase = true)) return true
            val targetLower = targetCategory.lowercase().trim()

            val cacheKey = if (item.id.isNotBlank()) "${item.id}_$targetLower" else null
            if (cacheKey != null) {
                categoryMatchCache[cacheKey]?.let { return it }
            }

            val result = computeMatchesCategory(item, targetCategory, targetLower)
            if (cacheKey != null) {
                categoryMatchCache[cacheKey] = result
            }
            return result
        }

        private fun computeMatchesCategory(item: MediaItem, targetCategory: String, targetLower: String): Boolean {
            // 0. Negative Guards (STEP 0: MUST RUN FIRST TO PREVENT CROSS-GENRE BLEED)
            val fullText = (item.title + " " + item.description + " " + item.genre).lowercase()

            // 0a. Negative Horror Guard
            if (targetLower in listOf("comedy", "romance", "love story", "family", "kids", "animation")) {
                if (HORROR_INDICATORS.any { fullText.contains(it) }) return false
            } else if (targetLower == "horror") {
                if (COMEDY_INDICATORS.any { fullText.contains(it) }) return false
            }

            // 0b. Negative Combat / Mercenary / Gritty Action Guard
            if (targetLower in listOf("comedy", "romance", "love story")) {
                val hasComedyKeyword = COMEDY_KEYWORDS_REGEX.containsMatchIn(fullText)
                if (!hasComedyKeyword && COMBAT_ACTION_REGEX.containsMatchIn(fullText)) {
                    return false
                }
            }

            val aliases = when (targetLower) {
                "love story", "romance" -> listOf("love story", "romance", "romantic", "love", "hypothesis")
                "action" -> listOf("action", "fight", "war", "strike", "combat")
                "drama" -> listOf("drama", "dramatic")
                "comedy" -> listOf("comedy", "comedic", "funny", "humor")
                "horror" -> listOf("horror", "scary", "ghost", "terror", "demon", "slasher")
                "adventure" -> listOf("adventure", "quest", "journey")
                "sci-fi", "sci fi" -> listOf("sci-fi", "sci fi", "science fiction")
                "thriller" -> listOf("thriller", "suspense")
                "crime" -> listOf("crime", "gangster", "cartel", "cop", "police", "mafia", "heist")
                "animation" -> listOf("animation", "animated", "anime", "cartoon")
                "family" -> listOf("family", "kids", "children")
                "mystery" -> listOf("mystery")
                "fantasy" -> listOf("fantasy", "magic")
                "martial arts", "kung fu", "kungu fu" -> listOf("martial arts", "kung fu", "kungu fu", "karate")
                "korean" -> listOf("korean", "k-drama", "kdrama", "korea")
                "indian" -> listOf("indian", "bollywood", "hindi", "india")
                "chinese" -> listOf("chinese", "china")
                "sport" -> listOf("sport", "sports", "boxing", "football", "soccer", "racing", "basketball")
                else -> listOf(targetLower)
            }

            // 1. Strict check on item.genre metadata:
            val itemGenreLower = item.genre.lowercase().trim()
            val isExplicitGenre = itemGenreLower.isNotBlank() && itemGenreLower != "movie" && itemGenreLower != "series" && itemGenreLower != "-" && itemGenreLower != "null"
            if (isExplicitGenre) {
                if (aliases.any { itemGenreLower.contains(it) }) {
                    recordItemCategory(item.id, targetCategory)
                    return true
                }

                val isConflictingGenre = KNOWN_GENRES.any { otherGenre ->
                    !aliases.any { it.contains(otherGenre) || otherGenre.contains(it) } && itemGenreLower.contains(otherGenre)
                }
                if (isConflictingGenre) {
                    return false
                }
            }

            // 2. Check streamUrl or posterUrl parameters
            val streamLower = item.streamUrl.lowercase()
            if (aliases.any { streamLower.contains("category=$it") || streamLower.contains("genre=$it") }) {
                recordItemCategory(item.id, targetCategory)
                return true
            }

            // 3. Check categoryCache only if item doesn't have an explicit conflicting genre
            if (item.id.isNotBlank()) {
                val cached = categoryCache[item.id]
                if (cached != null) {
                    val hasConflict = KNOWN_GENRES.any { otherGenre ->
                        !aliases.any { it.contains(otherGenre) || otherGenre.contains(it) } && cached.contains(otherGenre)
                    }
                    if (!hasConflict && (cached.contains(targetLower) || aliases.any { cached.contains(it) })) {
                        return true
                    }
                }
            }

            // 4. Check title keywords with pre-compiled patterns
            val titleLower = item.title.lowercase()
            val regexPairs = CATEGORY_KEYWORD_REGEX_MAP[targetCategory]
                ?: CATEGORY_KEYWORD_REGEX_MAP.entries.firstOrNull { it.key.equals(targetCategory, ignoreCase = true) }?.value
                ?: emptyList()

            if (regexPairs.any { (keyword, regex) ->
                if (regex != null) {
                    regex.containsMatchIn(titleLower)
                } else {
                    titleLower.contains(keyword)
                }
            }) {
                recordItemCategory(item.id, targetCategory)
                return true
            }

            // 5. Check description keywords for explicit genre indicators only
            if (item.description.isNotBlank()) {
                val explicitGenrePattern = EXPLICIT_GENRE_PATTERNS[targetLower]
                if (explicitGenrePattern != null) {
                    if (explicitGenrePattern.containsMatchIn(item.description)) {
                        recordItemCategory(item.id, targetCategory)
                        return true
                    }
                } else if (item.description.contains(targetLower, ignoreCase = true)) {
                    val fallbackRegex = Regex("""\b${Regex.escape(targetLower)} (film|movie|series)\b""", RegexOption.IGNORE_CASE)
                    if (fallbackRegex.containsMatchIn(item.description)) {
                        recordItemCategory(item.id, targetCategory)
                        return true
                    }
                }
            }

            return false
        }

        fun computeAvailableYears(items: List<MediaItem>): List<String> {
            val baseYears = (2026 downTo 2010).map { it.toString() } + listOf("2008", "2005", "2000", "1999", "1994")
            val itemYears = items.mapNotNull { extractYear(it) }
                .filter { (it.toIntOrNull() ?: 0) in 1980..2030 }
            val allDistinctYears = (baseYears + itemYears).distinct()
            return allDistinctYears.sortedByDescending { it.toIntOrNull() ?: 0 }
        }

        fun computeAvailableVjs(items: List<MediaItem>): List<String> {
            val dynamic = items.map { it.vj.trim() }
                .filter { it.isNotBlank() && it.length <= 25 }
                .distinct()
            return (DEFAULT_VJS + dynamic).distinct()
        }

        fun computeAvailableCategories(items: List<MediaItem>): List<String> {
            val dynamic = items.flatMap { item ->
                item.genre.split(",", "/", "&").map { it.trim() }
            }.filter { it.isNotBlank() && it.length <= 25 && !it.equals("Movie", ignoreCase = true) && !it.equals("Series", ignoreCase = true) }
            .distinct()
            return (DEFAULT_CATEGORIES + dynamic).distinct()
        }
    }

    private var filterJob: Job? = null

    private fun applySortAndFilter() {
        val currentState = _uiState.value
        val snapshot = synchronized(rawMovies) { rawMovies.filter { isMovieItem(it) } }
        var filtered = snapshot.distinctBy { it.id.ifBlank { it.streamUrl }.ifBlank { it.title } }

        // 1. Filter by Old Movies or Year if selected
        if (currentState.sortMode == MovieSortMode.OLD_MOVIES) {
            filtered = filtered.filter { isOldMovie(it) }
        } else if (currentState.selectedYear != null) {
            val y = currentState.selectedYear
            filtered = filtered.filter { matchesYear(it, y) }
        }

        // 2. Filter by VJ if selected
        if (!currentState.selectedVj.isNullOrBlank() && !currentState.selectedVj.equals("All VJs", ignoreCase = true)) {
            val vj = currentState.selectedVj
            filtered = filtered.filter { matchesVj(it, vj) }
        }

        // 3. Filter by Category if selected
        if (!currentState.selectedCategory.isNullOrBlank() && !currentState.selectedCategory.equals("All Categories", ignoreCase = true)) {
            val cat = currentState.selectedCategory
            filtered = filtered.filter { matchesCategory(it, cat) }
        }

        // 4. Sort or Reshuffle
        val sortedList = when (currentState.sortMode) {
            MovieSortMode.RANDOM -> filtered.shuffled()
            MovieSortMode.OLD_MOVIES, MovieSortMode.BY_YEAR -> {
                filtered.sortedWith(
                    compareByDescending<MediaItem> { extractYear(it)?.toIntOrNull() ?: 0 }
                        .thenByDescending { extractUploadTimestamp(it) ?: 0L }
                )
            }
            MovieSortMode.LATEST -> {
                if (currentState.selectedYear != null ||
                    (!currentState.selectedVj.isNullOrBlank() && !currentState.selectedVj.equals("All VJs", ignoreCase = true)) ||
                    (!currentState.selectedCategory.isNullOrBlank() && !currentState.selectedCategory.equals("All Categories", ignoreCase = true))) {
                    filtered.sortedWith(
                        compareByDescending<MediaItem> { extractUploadTimestamp(it) ?: 0L }
                            .thenByDescending { extractYear(it)?.toIntOrNull() ?: 0 }
                    )
                } else {
                    filtered
                }
            }
        }

        val distinctMovies = sortedList.distinctBy { it.id.ifBlank { it.streamUrl }.ifBlank { it.title } }
        val availableY = if (currentState.availableYears.isEmpty()) computeAvailableYears(snapshot) else currentState.availableYears
        val availableV = if (currentState.availableVjs.size <= DEFAULT_VJS.size) computeAvailableVjs(snapshot) else currentState.availableVjs
        val availableC = if (currentState.availableCategories.size <= DEFAULT_CATEGORIES.size) computeAvailableCategories(snapshot) else currentState.availableCategories

        _uiState.value = _uiState.value.copy(
            movies = distinctMovies,
            availableYears = availableY,
            availableVjs = availableV,
            availableCategories = availableC
        )
    }

    private fun appendLoadedMoviesToBottom(newItems: List<MediaItem>) {
        if (newItems.isEmpty()) return
        val currentState = _uiState.value
        val currentMovies = currentState.movies
        val vj = currentState.selectedVj
        val category = currentState.selectedCategory
        val year = currentState.selectedYear
        val isOldMode = currentState.sortMode == MovieSortMode.OLD_MOVIES
        val isVjSpecified = !vj.isNullOrBlank() && !vj.equals("All VJs", ignoreCase = true)
        val isCategorySpecified = !category.isNullOrBlank() && !category.equals("All Categories", ignoreCase = true) && !category.equals("All Genres", ignoreCase = true)

        val existingKeys = currentMovies.map { it.id.ifBlank { it.streamUrl }.ifBlank { it.title }.lowercase().trim() }.toSet()
        val toAppend = newItems.filter { item ->
            val key = item.id.ifBlank { item.streamUrl }.ifBlank { item.title }.lowercase().trim()
            key.isNotBlank() && key !in existingKeys &&
                (!isVjSpecified || matchesVj(item, vj)) &&
                (!isCategorySpecified || matchesCategory(item, category)) &&
                (year == null || matchesYear(item, year)) &&
                (!isOldMode || isOldMovie(item))
        }.distinctBy { it.id.ifBlank { it.streamUrl }.ifBlank { it.title } }

        if (toAppend.isNotEmpty()) {
            val snapshot = currentMovies + toAppend
            _uiState.value = _uiState.value.copy(
                movies = snapshot
            )
        }
    }

    fun selectCategory(category: String?) {
        loadJob?.cancel()
        categoryCache.clear()
        scrollIndex = 0
        scrollOffset = 0
        consecutiveEmptyLoads = 0
        categoryBatchPage = 1
        yearCatalogPage = 0
        yearCatalogEmptyStreak = 0
        val clean = if (category.isNullOrBlank() || category.equals("All Categories", ignoreCase = true) || category.equals("All Genres", ignoreCase = true)) null else category
        _uiState.value = _uiState.value.copy(
            selectedCategory = clean,
            canLoadMore = true
        )
        applySortAndFilter()
        if (_uiState.value.sortMode == MovieSortMode.RANDOM) {
            reshuffleBatchIndex = 0
            fetchMoviesForReshuffle(_uiState.value.selectedVj, clean)
        } else if (_uiState.value.sortMode == MovieSortMode.OLD_MOVIES) {
            fetchOldMovies(resetPage = true)
        } else if (_uiState.value.selectedYear != null || clean != null || _uiState.value.selectedVj != null) {
            fetchFilteredMovies(resetPage = true)
        }
    }

    fun clearCategoryFilter() {
        selectCategory(null)
    }

    fun selectVj(vj: String?) {
        loadJob?.cancel()
        categoryCache.clear()
        scrollIndex = 0
        scrollOffset = 0
        consecutiveEmptyLoads = 0
        vjBatchPage = 1
        yearCatalogPage = 0
        yearCatalogEmptyStreak = 0
        val clean = if (vj.isNullOrBlank() || vj.equals("All VJs", ignoreCase = true)) null else vj
        _uiState.value = _uiState.value.copy(
            selectedVj = clean,
            canLoadMore = true
        )
        applySortAndFilter()
        if (_uiState.value.sortMode == MovieSortMode.RANDOM) {
            reshuffleBatchIndex = 0
            fetchMoviesForReshuffle(clean, _uiState.value.selectedCategory)
        } else if (_uiState.value.sortMode == MovieSortMode.OLD_MOVIES) {
            fetchOldMovies(resetPage = true)
        } else if (_uiState.value.selectedYear != null || _uiState.value.selectedCategory != null || clean != null) {
            fetchFilteredMovies(resetPage = true)
        }
    }

    fun clearVjFilter() {
        selectVj(null)
    }

    private var reshuffleClickCount: Int = 0

    fun sortRandomly() {
        loadJob?.cancel()
        reshuffleClickCount++
        scrollIndex = 0
        scrollOffset = 0
        consecutiveEmptyLoads = 0
        categoryBatchPage = 1
        vjBatchPage = 1
        _uiState.value = _uiState.value.copy(
            sortMode = MovieSortMode.RANDOM,
            canLoadMore = true,
            isLoadingMore = true
        )
        // 1. Ingest all available cached movies across all categories and years
        val cachedAll = MediaCache.getAll().filter { isMovieItem(it) }
        if (cachedAll.isNotEmpty()) {
            synchronized(rawMovies) {
                val existingIds = rawMovies.map { it.id }.toSet()
                val newUnique = cachedAll.filter { isMovieItem(it) && it.id !in existingIds }
                if (newUnique.isNotEmpty()) {
                    rawMovies.addAll(newUnique)
                }
            }
        }
        applySortAndFilter()
        // 2. Fetch fresh movies for reshuffle based on selected VJ and selected Category
        reshuffleBatchIndex = (reshuffleBatchIndex + 1) % 50
        fetchMoviesForReshuffle(_uiState.value.selectedVj, _uiState.value.selectedCategory)
    }

    fun setSortLatest() {
        loadJob?.cancel()
        oldMoviesJob?.cancel()
        scrollIndex = 0
        scrollOffset = 0
        _uiState.value = _uiState.value.copy(
            sortMode = MovieSortMode.LATEST,
            selectedYear = null,
            selectedVj = null,
            selectedCategory = null,
            isYearSelectorOpen = false,
            canLoadMore = true
        )
        applySortAndFilter()
    }

    fun toggleOldMovies() {
        loadJob?.cancel()
        scrollIndex = 0
        scrollOffset = 0
        if (_uiState.value.sortMode == MovieSortMode.OLD_MOVIES) {
            setSortLatest()
            return
        }

        _uiState.value = _uiState.value.copy(
            sortMode = MovieSortMode.OLD_MOVIES,
            selectedYear = null,
            isYearSelectorOpen = false,
            canLoadMore = true
        )

        // 1. Ingest all available cached old movies
        val allCached = MediaCache.getAll().filter { isMovieItem(it) && isOldMovie(it) }
        if (allCached.isNotEmpty()) {
            synchronized(rawMovies) {
                val existingIds = rawMovies.map { it.id }.toSet()
                val newUnique = allCached.filter { it.id !in existingIds }
                if (newUnique.isNotEmpty()) {
                    rawMovies.addAll(newUnique)
                }
            }
        }

        applySortAndFilter()
        fetchOldMovies(resetPage = true)
    }

    fun toggleYearSelector() {
        scrollIndex = 0
        scrollOffset = 0
        val currentMode = _uiState.value.sortMode
        if (currentMode != MovieSortMode.BY_YEAR) {
            _uiState.value = _uiState.value.copy(
                sortMode = MovieSortMode.BY_YEAR,
                isYearSelectorOpen = true
            )
            applySortAndFilter()
            return
        }
        val nextOpen = !_uiState.value.isYearSelectorOpen
        _uiState.value = _uiState.value.copy(
            isYearSelectorOpen = nextOpen,
            sortMode = if (nextOpen) MovieSortMode.BY_YEAR else MovieSortMode.LATEST
        )
        applySortAndFilter()
    }

    fun selectYear(year: String) {
        loadJob?.cancel()
        categoryCache.clear()
        scrollIndex = 0
        scrollOffset = 0
        yearBatchIndex = 0
        yearCatalogPage = 0
        yearCatalogEmptyStreak = 0
        // Instantly load any movies cached for this year in MediaCache
        val cachedForYear = MediaCache.getYearMovies(year)?.filter { isMovieItem(it) }
        val hasCached = !cachedForYear.isNullOrEmpty()
        synchronized(rawMovies) {
            if (hasCached) {
                val existingIds = rawMovies.map { it.id }.toSet()
                val newUnique = cachedForYear!!.filter { isMovieItem(it) && it.id !in existingIds }
                if (newUnique.isNotEmpty()) {
                    rawMovies.addAll(newUnique)
                }
            }
            val allCachedMatching = MediaCache.getAll().filter { isMovieItem(it) && matchesYear(it, year) }
            if (allCachedMatching.isNotEmpty()) {
                val existingIds = rawMovies.map { it.id }.toSet()
                val newUnique = allCachedMatching.filter { isMovieItem(it) && it.id !in existingIds }
                if (newUnique.isNotEmpty()) {
                    rawMovies.addAll(newUnique)
                }
            }
        }
        val hasAnyMatching = hasCached || synchronized(rawMovies) { rawMovies.any { isMovieItem(it) && matchesYear(it, year) } }
        _uiState.value = _uiState.value.copy(
            selectedYear = year,
            sortMode = if (_uiState.value.sortMode == MovieSortMode.RANDOM) MovieSortMode.RANDOM else MovieSortMode.BY_YEAR,
            isYearSelectorOpen = true,
            isLoading = !hasAnyMatching,
            canLoadMore = true
        )
        applySortAndFilter()
        fetchFilteredMovies(resetPage = true)
    }

    fun clearYearFilter() {
        scrollIndex = 0
        scrollOffset = 0
        _uiState.value = _uiState.value.copy(
            selectedYear = null,
            canLoadMore = true
        )
        applySortAndFilter()
        if (_uiState.value.selectedCategory != null || _uiState.value.selectedVj != null) {
            fetchFilteredMovies(resetPage = true)
        }
    }

    private fun fetchFilteredMovies(resetPage: Boolean = true) {
        filterFetchJob?.cancel()
        if (resetPage) {
            filteredQueryPage = 1
            vjBatchPage = 1
            categoryBatchPage = 1
            consecutiveEmptyLoads = 0
        }
        val year = _uiState.value.selectedYear
        val category = _uiState.value.selectedCategory
        val vj = _uiState.value.selectedVj
        val pageToFetch = filteredQueryPage

        val hasAnyFilter = year != null ||
            (!category.isNullOrBlank() && !category.equals("All Categories", ignoreCase = true) && !category.equals("All Genres", ignoreCase = true)) ||
            (!vj.isNullOrBlank() && !vj.equals("All VJs", ignoreCase = true))

        if (!hasAnyFilter) return

        filterFetchJob = viewModelScope.launch(ioDispatcher) {
            try {
                val isVjSpecified = !vj.isNullOrBlank() && !vj.equals("All VJs", ignoreCase = true)
                val isCategorySpecified = !category.isNullOrBlank() && !category.equals("All Categories", ignoreCase = true) && !category.equals("All Genres", ignoreCase = true)

                if (resetPage) {
                    val matchingInMemory = synchronized(rawMovies) {
                        rawMovies.count { isMovieItem(it) &&
                            (year == null || matchesYear(it, year)) &&
                            (category == null || matchesCategory(it, category)) &&
                            (vj == null || matchesVj(it, vj))
                        }
                    }
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = matchingInMemory == 0,
                            isLoadingMore = matchingInMemory > 0
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(isLoadingMore = true)
                    }
                }

                val foundItems = mutableListOf<MediaItem>()

                // 1. Twolekede filtered catalog
                val res = movieRepository.getFilteredMovies(
                    year = year,
                    category = category,
                    vj = vj,
                    page = pageToFetch,
                    perPage = 24
                )
                foundItems.addAll(res.getOrDefault(emptyList()).filter { isMovieItem(it) })

                // 2. Fetch directly from VJ catalog
                if (isVjSpecified) {
                    val vjId = findVjId(vj)
                    if (vjId != null) {
                        if (isCategorySpecified) {
                            val genreId = findGenreId(category)
                            if (genreId != null) {
                                try {
                                    val r = movieRepository.getMoviesByVjAndGenre(vjId, genreId, page = pageToFetch)
                                    foundItems.addAll(r.getOrDefault(emptyList()).filter { isMovieItem(it) })
                                } catch (_: Exception) {}
                            }
                        } else {
                            try {
                                val r = movieRepository.getMoviesByVj(vjId, page = pageToFetch)
                                foundItems.addAll(r.getOrDefault(emptyList()).filter { isMovieItem(it) })
                            } catch (_: Exception) {}
                        }
                    }
                }

                // 3. Fetch directly from Genre catalog
                if (isCategorySpecified) {
                    val genreId = findGenreId(category)
                    if (genreId != null) {
                        val matchedGenre = loadedGenreCategories.find { it.id == genreId }
                        val pipe = matchedGenre?.pipe ?: "genre_vj"
                        try {
                            val r = movieRepository.getMoviesByGenre(genreId, null, pipe, pageToFetch)
                            foundItems.addAll(r.getOrDefault(emptyList()).filter { isMovieItem(it) })
                        } catch (_: Exception) {}
                    }
                }

                // 4. Targeted Genre Search Discovery
                if (isCategorySpecified && searchRepository != null) {
                    val catQueries = CATEGORY_DISCOVERY_QUERIES[category] ?: listOf(category!!)
                    val start = (pageToFetch * 2) % catQueries.size
                    val queriesToRun = mutableListOf(catQueries[start % catQueries.size], catQueries[(start + 1) % catQueries.size])
                    if (isVjSpecified) {
                        queriesToRun.add("$vj ${catQueries[start % catQueries.size]}")
                    }
                    for (q in queriesToRun.distinct()) {
                        try {
                            val r = searchRepository.searchContent(q).getOrDefault(emptyList())
                            foundItems.addAll(r.filter { isMovieItem(it) })
                        } catch (_: Exception) {}
                    }
                }

                val filteredFound = foundItems.filter { item ->
                    (!isCategorySpecified || matchesCategory(item, category)) &&
                    (!isVjSpecified || matchesVj(item, vj))
                }

                if (filteredFound.isNotEmpty()) {
                    val tagged = filteredFound.map { item ->
                        var u = item
                        if (year != null) {
                            yearCache[u.id] = year
                            u = u.copy(year = year)
                        }
                        if (isCategorySpecified) {
                            recordItemCategory(u.id, category!!)
                            if (u.genre.isBlank() || u.genre == "-") {
                                u = u.copy(genre = category)
                            }
                        }
                        if (isVjSpecified && (u.vj.isBlank() || u.vj == "-") && matchesVj(u, vj)) {
                            u = u.copy(vj = vj!!)
                        }
                        u
                    }

                    val newUnique = synchronized(rawMovies) {
                        val existingIds = rawMovies.map { it.id }.toSet()
                        val filtered = tagged.filter { it.id !in existingIds }
                        rawMovies.addAll(filtered)
                        filtered
                    }
                    if (newUnique.isNotEmpty()) {
                        MediaCache.putAll(newUnique)
                    }
                    if (year != null) {
                        val allForYear = synchronized(rawMovies) { rawMovies.filter { matchesYear(it, year) } }
                        MediaCache.setYearMovies(year, allForYear)
                    }

                    filteredQueryPage++
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            canLoadMore = true,
                            isLoading = false,
                            isLoadingMore = false
                        )
                        if (resetPage) {
                            applySortAndFilter()
                        } else {
                            appendLoadedMoviesToBottom(newUnique)
                        }
                    }
                } else {
                    filteredQueryPage++
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            canLoadMore = true,
                            isLoading = false,
                            isLoadingMore = false
                        )
                        if (resetPage) {
                            applySortAndFilter()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoadingMore = false
                    )
                }
            }
        }
    }

    private fun fetchOldMovies(resetPage: Boolean = true) {
        oldMoviesJob?.cancel()
        if (resetPage) {
            oldMoviesBatchIndex = 0
            vjBatchPage = 1
            categoryBatchPage = 1
            consecutiveEmptyLoads = 0
        }
        val vj = _uiState.value.selectedVj
        val category = _uiState.value.selectedCategory
        val isVjSpecified = !vj.isNullOrBlank() && !vj.equals("All VJs", ignoreCase = true)
        val isCategorySpecified = !category.isNullOrBlank() && !category.equals("All Categories", ignoreCase = true) && !category.equals("All Genres", ignoreCase = true)

        val matchingCount = synchronized(rawMovies) {
            rawMovies.count { isMovieItem(it) && isOldMovie(it) &&
                (!isVjSpecified || matchesVj(it, vj)) &&
                (!isCategorySpecified || matchesCategory(it, category))
            }
        }

        if (resetPage && matchingCount == 0) {
            _uiState.value = _uiState.value.copy(isLoading = true, isLoadingMore = false)
        } else {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
        }

        oldMoviesJob = viewModelScope.launch(ioDispatcher) {
            try {
                val foundItems = mutableListOf<MediaItem>()
                val vjId = if (isVjSpecified) findVjId(vj) else null
                val genreId = if (isCategorySpecified) findGenreId(category) else null

                // 1. Fetch directly from Twolekede VJ catalog
                if (isVjSpecified && vjId != null) {
                    if (isCategorySpecified && genreId != null) {
                        try {
                            val res = movieRepository.getMoviesByVjAndGenre(vjId, genreId, page = vjBatchPage)
                            foundItems.addAll(res.getOrDefault(emptyList()).filter { isMovieItem(it) })
                        } catch (_: Exception) {}
                    } else {
                        try {
                            val res = movieRepository.getMoviesByVj(vjId, page = vjBatchPage)
                            foundItems.addAll(res.getOrDefault(emptyList()).filter { isMovieItem(it) })
                        } catch (_: Exception) {}
                    }
                }

                // 2. Fetch directly from Genre catalog
                if (isCategorySpecified && genreId != null) {
                    val matchedGenre = loadedGenreCategories.find { it.id == genreId }
                    val pipe = matchedGenre?.pipe ?: "genre_vj"
                    try {
                        val res = movieRepository.getMoviesByGenre(genreId, null, pipe, categoryBatchPage)
                        foundItems.addAll(res.getOrDefault(emptyList()).filter { isMovieItem(it) })
                    } catch (_: Exception) {}
                }

                // 3. Twolekede filtered catalog
                try {
                    val res = movieRepository.getFilteredMovies(
                        year = null,
                        category = category,
                        vj = vj,
                        page = (oldMoviesBatchIndex % 10) + 1,
                        perPage = 24
                    )
                    foundItems.addAll(res.getOrDefault(emptyList()).filter { isMovieItem(it) })
                } catch (_: Exception) {}

                // 4. Targeted Search Discovery for classic old titles
                if (searchRepository != null) {
                    val searchTerms = mutableListOf<String>()
                    if (isCategorySpecified) {
                        val catQueries = CATEGORY_DISCOVERY_QUERIES[category] ?: listOf(category!!)
                        val start = (oldMoviesBatchIndex * 2) % catQueries.size
                        val q1 = catQueries[start % catQueries.size]
                        val q2 = catQueries[(start + 1) % catQueries.size]
                        if (isVjSpecified) {
                            searchTerms.add("$vj $q1")
                            searchTerms.add("$vj $q2")
                            searchTerms.add("$vj $category")
                        } else {
                            searchTerms.add(q1)
                            searchTerms.add(q2)
                        }
                    } else if (isVjSpecified) {
                        val start = (oldMoviesBatchIndex * 2) % CURATED_OLD_QUERIES.size
                        searchTerms.add("$vj ${CURATED_OLD_QUERIES[start % CURATED_OLD_QUERIES.size]}")
                        searchTerms.add("$vj ${CURATED_OLD_QUERIES[(start + 1) % CURATED_OLD_QUERIES.size]}")
                    } else {
                        val start = (oldMoviesBatchIndex * 2) % CURATED_OLD_QUERIES.size
                        searchTerms.add(CURATED_OLD_QUERIES[start % CURATED_OLD_QUERIES.size])
                        searchTerms.add(CURATED_OLD_QUERIES[(start + 1) % CURATED_OLD_QUERIES.size])
                    }

                    for (q in searchTerms.distinct()) {
                        try {
                            val res = searchRepository.searchContent(q).getOrDefault(emptyList())
                            foundItems.addAll(res.filter { isMovieItem(it) })
                        } catch (_: Exception) {}
                    }
                }

                val filteredFound = foundItems.filter { item ->
                    (!isCategorySpecified || matchesCategory(item, category)) &&
                    (!isVjSpecified || matchesVj(item, vj))
                }

                if (filteredFound.isNotEmpty()) {
                    val tagged = filteredFound.map { item ->
                        var u = item
                        if (isCategorySpecified) {
                            recordItemCategory(u.id, category!!)
                            if (u.genre.isBlank() || u.genre == "-") u = u.copy(genre = category)
                        }
                        if (isVjSpecified && (u.vj.isBlank() || u.vj == "-") && matchesVj(u, vj)) {
                            u = u.copy(vj = vj!!)
                        }
                        u
                    }

                    val newUnique = synchronized(rawMovies) {
                        val existingIds = rawMovies.map { it.id }.toSet()
                        val filtered = tagged.filter { it.id !in existingIds }
                        rawMovies.addAll(filtered)
                        filtered
                    }
                    if (newUnique.isNotEmpty()) {
                        MediaCache.putAll(newUnique)
                    }
                }

                oldMoviesBatchIndex++
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        canLoadMore = true
                    )
                    applySortAndFilter()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoadingMore = false
                    )
                }
            }
        }
    }

    private suspend fun loadMoreOldMovies(): List<MediaItem> {
        val newlyAdded = mutableListOf<MediaItem>()
        try {
            oldMoviesBatchIndex++
            vjBatchPage++
            categoryBatchPage++

            val vj = _uiState.value.selectedVj
            val category = _uiState.value.selectedCategory
            val isVjSpecified = !vj.isNullOrBlank() && !vj.equals("All VJs", ignoreCase = true)
            val isCategorySpecified = !category.isNullOrBlank() && !category.equals("All Categories", ignoreCase = true) && !category.equals("All Genres", ignoreCase = true)
            val vjId = if (isVjSpecified) findVjId(vj) else null
            val genreId = if (isCategorySpecified) findGenreId(category) else null

            val foundItems = mutableListOf<MediaItem>()

            // 1. Fetch next page from VJ catalog on media server
            if (isVjSpecified && vjId != null) {
                if (isCategorySpecified && genreId != null) {
                    try {
                        val res = movieRepository.getMoviesByVjAndGenre(vjId, genreId, page = vjBatchPage)
                        foundItems.addAll(res.getOrDefault(emptyList()).filter { isMovieItem(it) })
                    } catch (_: Exception) {}
                } else {
                    try {
                        val res = movieRepository.getMoviesByVj(vjId, page = vjBatchPage)
                        foundItems.addAll(res.getOrDefault(emptyList()).filter { isMovieItem(it) })
                    } catch (_: Exception) {}
                }
            }

            // 2. Fetch next page from Genre catalog on media server
            if (isCategorySpecified && genreId != null) {
                val matchedGenre = loadedGenreCategories.find { it.id == genreId }
                val pipe = matchedGenre?.pipe ?: "genre_vj"
                try {
                    val res = movieRepository.getMoviesByGenre(genreId, null, pipe, categoryBatchPage)
                    foundItems.addAll(res.getOrDefault(emptyList()).filter { isMovieItem(it) })
                } catch (_: Exception) {}
            }

            // 3. Twolekede filtered catalog next page
            try {
                val res = movieRepository.getFilteredMovies(
                    year = null,
                    category = category,
                    vj = vj,
                    page = oldMoviesBatchIndex + 1,
                    perPage = 24
                )
                foundItems.addAll(res.getOrDefault(emptyList()).filter { isMovieItem(it) })
            } catch (_: Exception) {}

            // 4. Curated blockbuster search discovery
            if (searchRepository != null) {
                val queriesToRun = mutableListOf<String>()
                if (isCategorySpecified) {
                    val catList = CATEGORY_DISCOVERY_QUERIES[category] ?: listOf(category!!)
                    val start = (oldMoviesBatchIndex * 2) % catList.size
                    if (isVjSpecified) {
                        queriesToRun.add("$vj ${catList[start % catList.size]}")
                        queriesToRun.add("$vj ${catList[(start + 1) % catList.size]}")
                        queriesToRun.add("$vj $category")
                    } else {
                        queriesToRun.add(catList[start % catList.size])
                        queriesToRun.add(catList[(start + 1) % catList.size])
                    }
                } else if (isVjSpecified) {
                    val start = (oldMoviesBatchIndex * 2) % CURATED_OLD_QUERIES.size
                    queriesToRun.add("$vj ${CURATED_OLD_QUERIES[start % CURATED_OLD_QUERIES.size]}")
                    queriesToRun.add("$vj ${CURATED_OLD_QUERIES[(start + 1) % CURATED_OLD_QUERIES.size]}")
                } else {
                    val start = (oldMoviesBatchIndex * 2) % CURATED_OLD_QUERIES.size
                    queriesToRun.add(CURATED_OLD_QUERIES[start % CURATED_OLD_QUERIES.size])
                    queriesToRun.add(CURATED_OLD_QUERIES[(start + 1) % CURATED_OLD_QUERIES.size])
                }

                for (q in queriesToRun.distinct()) {
                    try {
                        val res = searchRepository.searchContent(q).getOrDefault(emptyList())
                        foundItems.addAll(res.filter { isMovieItem(it) })
                    } catch (_: Exception) {}
                }
            }

            // 5. Deep catalog page discovery (pages 5..35) only when no category and no VJ is specified
            if (!isCategorySpecified && !isVjSpecified) {
                val catalogPage = (oldMoviesBatchIndex % 30) + 5
                try {
                    val catRes = movieRepository.getLatestMovies(null, catalogPage)
                    foundItems.addAll(catRes.getOrDefault(emptyList()).filter { isMovieItem(it) })
                } catch (_: Exception) {}
            }

            val filteredFound = foundItems.filter { item ->
                (!isCategorySpecified || matchesCategory(item, category)) &&
                (!isVjSpecified || matchesVj(item, vj))
            }

            if (filteredFound.isNotEmpty()) {
                val tagged = filteredFound.map { item ->
                    var u = item
                    if (isCategorySpecified) {
                        recordItemCategory(u.id, category!!)
                        if (u.genre.isBlank() || u.genre == "-") u = u.copy(genre = category)
                    }
                    if (isVjSpecified && (u.vj.isBlank() || u.vj == "-") && matchesVj(u, vj)) u = u.copy(vj = vj!!)
                    u
                }
                val newUnique = synchronized(rawMovies) {
                    val existingIds = rawMovies.map { it.id }.toSet()
                    val filtered = tagged.filter { it.id !in existingIds }
                    rawMovies.addAll(filtered)
                    filtered
                }
                if (newUnique.isNotEmpty()) {
                    MediaCache.putAll(newUnique)
                    newlyAdded.addAll(newUnique)
                }
            }
        } catch (_: Exception) {}
        return newlyAdded
    }

    private fun getYearDiscoveryQueries(year: String, category: String? = null, vj: String? = null): List<String> {
        val baseTitles = YEAR_DISCOVERY_QUERIES[year] ?: emptyList()
        val queries = mutableListOf<String>()

        val hasCategory = !category.isNullOrBlank() && !category.equals("All Categories", ignoreCase = true) && !category.equals("All Genres", ignoreCase = true)
        val hasVj = !vj.isNullOrBlank() && !vj.equals("All VJs", ignoreCase = true)

        if (hasCategory && hasVj) {
            queries.add("$vj $category $year")
            val catQueries = CATEGORY_DISCOVERY_QUERIES[category] ?: emptyList()
            catQueries.take(8).forEach { queries.add("$vj $it $year") }
            queries.add("$category $year")
        } else if (hasCategory) {
            queries.add("$category $year")
            val catQueries = CATEGORY_DISCOVERY_QUERIES[category] ?: emptyList()
            // High-yield category queries
            catQueries.take(10).forEach { queries.add("$it $year") }
            queries.add("VJ Junior $category $year")
            queries.add("VJ Emmy $category $year")
            queries.add("VJ Jingo $category $year")
            queries.add("VJ Ice P $category $year")
        } else if (hasVj) {
            queries.add("$vj $year")
            baseTitles.take(8).forEach { queries.add("$vj $it") }
            queries.add("$vj Action $year")
            queries.add("$vj Drama $year")
        } else {
            // General year discovery
            queries.add(year)
            queries.add("VJ Junior $year")
            queries.add("VJ Emmy $year")
            queries.add("VJ Ice P $year")
            queries.add("VJ Jingo $year")
            queries.add("VJ Tom $year")
            queries.add("Action $year")
            queries.add("Drama $year")
            queries.add("Comedy $year")
            queries.add("Horror $year")
            queries.add("Sci-Fi $year")
            queries.add("Adventure $year")
        }

        // Add curated blockbuster titles for this year
        queries.addAll(baseTitles)
        return queries.distinct()
    }

    private fun fetchAdditionalMoviesForYear(year: String) {
        yearFetchJob?.cancel()
        yearFetchJob = viewModelScope.launch(ioDispatcher) {
            try {
                val category = _uiState.value.selectedCategory
                val vj = _uiState.value.selectedVj
                val isCategorySpecified = !category.isNullOrBlank() && !category.equals("All Categories", ignoreCase = true) && !category.equals("All Genres", ignoreCase = true)
                val isVjSpecified = !vj.isNullOrBlank() && !vj.equals("All VJs", ignoreCase = true)

                // 1. Ingest matching items from local MediaCache
                val cachedAll = MediaCache.getAll().filter { isMovieItem(it) && matchesYear(it, year) }
                if (cachedAll.isNotEmpty()) {
                    val newUnique = synchronized(rawMovies) {
                        val existingIds = rawMovies.map { it.id }.toSet()
                        val filtered = cachedAll.filter { isMovieItem(it) && it.id !in existingIds }
                        rawMovies.addAll(filtered)
                        filtered
                    }
                    if (newUnique.isNotEmpty()) {
                        applySortAndFilter()
                    }
                }

                // If category specified, ingest from genre cache
                if (isCategorySpecified) {
                    val genreId = findGenreId(category)
                    if (genreId != null) {
                        val cachedGenre = MediaCache.getGenreMovies(genreId)?.filter { isMovieItem(it) && matchesYear(it, year) } ?: emptyList()
                        val newUnique = synchronized(rawMovies) {
                            val existingIds = rawMovies.map { it.id }.toSet()
                            val filtered = cachedGenre.filter { isMovieItem(it) && it.id !in existingIds }
                            rawMovies.addAll(filtered)
                            filtered
                        }
                        if (newUnique.isNotEmpty()) {
                            applySortAndFilter()
                        }
                    }
                }

                // 2. Fetch direct year search queries concurrently in parallel
                val queries = getYearDiscoveryQueries(year, category, vj)
                val firstBatch = queries.take(8)

                if (searchRepository != null && firstBatch.isNotEmpty()) {
                    val results = withContext(ioDispatcher) {
                        coroutineScope {
                            firstBatch.map { q ->
                                async {
                                    try {
                                        searchRepository.searchContent(q).getOrDefault(emptyList())
                                    } catch (_: Exception) {
                                        emptyList()
                                    }
                                }
                            }.awaitAll().flatten()
                        }
                    }

                    val matching = results.filter { item ->
                        isMovieItem(item) &&
                        (!isVjSpecified || matchesVj(item, vj)) &&
                        (!isCategorySpecified || matchesCategory(item, category))
                    }
                    if (matching.isNotEmpty()) {
                        val tagged = matching.map { item ->
                            val detectedYear = extractYear(item)
                            var u = if (detectedYear == year || detectedYear == null) {
                                yearCache[item.id] = year
                                item.copy(year = year)
                            } else {
                                item
                            }
                            if (isCategorySpecified) {
                                if (matchesCategory(u, category)) {
                                    recordItemCategory(u.id, category!!)
                                }
                            }
                            if (isVjSpecified && (u.vj.isBlank() || u.vj == "-") && matchesVj(u, vj)) {
                                u = u.copy(vj = vj!!)
                            }
                            u
                        }

                        val newUnique = synchronized(rawMovies) {
                            val existingIds = rawMovies.map { it.id }.toSet()
                            val filtered = tagged.filter { it.id !in existingIds }
                            rawMovies.addAll(filtered)
                            filtered
                        }
                        if (newUnique.isNotEmpty()) {
                            MediaCache.putAll(newUnique)
                        }

                        val allForThisYear = synchronized(rawMovies) { rawMovies.filter { matchesYear(it, year) } }
                        if (allForThisYear.isNotEmpty()) {
                            MediaCache.setYearMovies(year, allForThisYear)
                        }
                    }

                    yearBatchIndex = 1
                    applySortAndFilter()
                }

                // 3. If category specified, fetch multiple pages from genre endpoint (pages 1..6)
                if (isCategorySpecified) {
                    val genreId = findGenreId(category)
                    if (genreId != null) {
                        val matchedGenre = loadedGenreCategories.find { it.id == genreId }
                        val pipe = matchedGenre?.pipe ?: "genre_vj"
                        for (gp in 1..6) {
                            val res = movieRepository.getMoviesByGenre(genreId, null, pipe, gp)
                            val items = res.getOrDefault(emptyList()).filter { isMovieItem(it) }
                            if (items.isEmpty()) break
                            val tagged = items.map { item ->
                                recordItemCategory(item.id, category!!)
                                val ey = extractYear(item)
                                val u = if (ey == year || matchesYear(item, year) || ey == null) {
                                    yearCache[item.id] = year
                                    item.copy(year = year)
                                } else item
                                u
                            }
                            val newUnique = synchronized(rawMovies) {
                                val existingIds = rawMovies.map { it.id }.toSet()
                                val filtered = tagged.filter { it.id !in existingIds }
                                rawMovies.addAll(filtered)
                                filtered
                            }
                            if (newUnique.isNotEmpty()) {
                                MediaCache.putAll(newUnique)
                            }
                            applySortAndFilter()
                            // If we already have plenty of matching movies for this year, don't over-fetch
                            val yearMatches = synchronized(rawMovies) { rawMovies.count { matchesYear(it, year) && matchesCategory(it, category) } }
                            if (yearMatches >= 25) {
                                categoryBatchPage = gp
                                break
                            }
                            categoryBatchPage = gp
                            kotlinx.coroutines.delay(100)
                        }
                    }
                }

                // 4. Fetch multiple catalog pages and filter by year to bulk-discover movies
                for (catalogPage in 1..5) {
                    kotlinx.coroutines.delay(100)
                    val catalogRes = movieRepository.getLatestMovies(null, catalogPage)
                    val catalogItems = catalogRes.getOrDefault(emptyList()).filter { isMovieItem(it) }
                    if (catalogItems.isEmpty()) break
                    val taggedNew = catalogItems.map { item ->
                        val ey = extractYear(item)
                        if (ey == year || matchesYear(item, year)) {
                            yearCache[item.id] = year
                            item.copy(year = year)
                        } else item
                    }
                    val newUnique = synchronized(rawMovies) {
                        val existingIds = rawMovies.map { it.id }.toSet()
                        val filtered = taggedNew.filter { it.id !in existingIds }
                        rawMovies.addAll(filtered)
                        filtered
                    }
                    if (newUnique.isNotEmpty()) {
                        MediaCache.putAll(newUnique)
                    }
                    yearCatalogPage = catalogPage
                }

                val allForThisYear = synchronized(rawMovies) { rawMovies.filter { matchesYear(it, year) } }
                if (allForThisYear.isNotEmpty()) {
                    MediaCache.setYearMovies(year, allForThisYear)
                }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(canLoadMore = true)
                }
                applySortAndFilter()
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    private fun findGenreId(category: String?): Int? {
        if (category.isNullOrBlank()) return null
        val targetLower = category.lowercase().trim()
        val fromLoaded = loadedGenreCategories.find {
            it.name.trim().equals(targetLower, ignoreCase = true) ||
            matchesCategory(MediaItem(id = "", title = "", genre = it.name), category)
        }?.id
        if (fromLoaded != null) return fromLoaded
        return KNOWN_GENRE_IDS[targetLower]
            ?: KNOWN_GENRE_IDS.entries.find { targetLower.contains(it.key) || it.key.contains(targetLower) }?.value
    }

    private fun findVjId(vj: String?): Int? {
        if (vj.isNullOrBlank() || vj.equals("All VJs", ignoreCase = true)) return null
        val clean = vj.lowercase().replace("vj", "").replace(".", "").trim()
        val fromLoaded = loadedVjCategories.find {
            val catClean = it.name.lowercase().replace("vj", "").replace(".", "").trim()
            catClean.isNotBlank() && (catClean == clean || catClean.contains(clean) || clean.contains(catClean))
        }?.id
        if (fromLoaded != null) return fromLoaded
        return KNOWN_VJ_IDS[clean]
            ?: KNOWN_VJ_IDS.entries.find { clean.contains(it.key) || it.key.contains(clean) }?.value
    }

    private fun inferCategory(item: MediaItem, targetCategory: String): Boolean {
        return matchesCategory(item, targetCategory)
    }

    private fun fetchMoviesForReshuffle(vj: String? = null, category: String? = null) {
        reshuffleJob?.cancel()
        reshuffleJob = viewModelScope.launch(ioDispatcher) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoadingMore = true)
                }
                val isVjSpecified = !vj.isNullOrBlank() && !vj.equals("All VJs", ignoreCase = true)
                val isCategorySpecified = !category.isNullOrBlank() && !category.equals("All Categories", ignoreCase = true)

                val genreId = if (isCategorySpecified) findGenreId(category) else null
                val vjId = if (isVjSpecified) findVjId(vj) else null

                // 1. Ingest matching items from local MediaCache
                val cachedGenreItems = if (genreId != null) MediaCache.getGenreMovies(genreId) ?: emptyList() else emptyList()
                val cachedVjItems = if (vjId != null) MediaCache.getVjMovies(vjId) ?: emptyList() else emptyList()
                val cachedGeneral = MediaCache.getAll().filter { isMovieItem(it) }

                val initialPool = (cachedGenreItems + cachedVjItems + cachedGeneral).distinctBy { it.id }
                if (initialPool.isNotEmpty()) {
                    val toAdd = mutableListOf<MediaItem>()
                    for (item in initialPool) {
                        var updated = item
                        if (isCategorySpecified && (cachedGenreItems.any { it.id == item.id } || matchesCategory(item, category))) {
                            recordItemCategory(item.id, category!!)
                        }
                        if (isVjSpecified && (cachedVjItems.any { it.id == item.id } || matchesVj(item, vj))) {
                            if (updated.vj.isBlank()) updated = updated.copy(vj = vj!!)
                        }
                        toAdd.add(updated)
                    }

                    val toAddMap = toAdd.associateBy { it.id }
                    synchronized(rawMovies) {
                        for (i in rawMovies.indices) {
                            toAddMap[rawMovies[i].id]?.let { meta ->
                                var ex = rawMovies[i]
                                if (ex.genre.isBlank() && meta.genre.isNotBlank()) ex = ex.copy(genre = meta.genre)
                                if (ex.vj.isBlank() && meta.vj.isNotBlank()) ex = ex.copy(vj = meta.vj)
                                rawMovies[i] = ex
                            }
                        }
                    }

                    val existingIds = synchronized(rawMovies) { rawMovies.map { it.id }.toSet() }
                    val newUnique = toAdd.filter { isMovieItem(it) && it.id !in existingIds }
                    if (newUnique.isNotEmpty()) {
                        synchronized(rawMovies) {
                            rawMovies.addAll(newUnique)
                        }
                    }
                    applySortAndFilter()
                }

                // 2. Fetch fresh catalog page from movieRepository (Twolekede / grid slots)
                val catalogPage = (reshuffleBatchIndex % 15) + 2
                val catalogRes = movieRepository.getLatestMovies(null, catalogPage)
                val catalogItems = catalogRes.getOrDefault(emptyList()).filter { isMovieItem(it) }
                if (catalogItems.isNotEmpty()) {
                    val existingIds = synchronized(rawMovies) { rawMovies.map { it.id }.toSet() }
                    val newUnique = catalogItems.filter { isMovieItem(it) && it.id !in existingIds }
                    if (newUnique.isNotEmpty()) {
                        synchronized(rawMovies) {
                            rawMovies.addAll(newUnique)
                        }
                        MediaCache.putAll(newUnique)
                    }
                }

                // 3. If category is specified, fetch from genre catalog (multiple pages)
                if (isCategorySpecified && genreId != null) {
                    val matchedGenre = loadedGenreCategories.find { it.id == genreId }
                    val pipe = matchedGenre?.pipe ?: "genre_vj"

                    for (p in 1..3) {
                        val genreResult = movieRepository.getMoviesByGenre(genreId, null, pipe, p)
                        val genreItems = genreResult.getOrDefault(emptyList()).filter { isMovieItem(it) }
                        if (genreItems.isEmpty()) break

                        val tagged = genreItems.map { item ->
                            recordItemCategory(item.id, category!!)
                            var u = item
                            if (isVjSpecified && matchesVj(u, vj) && u.vj.isBlank()) u = u.copy(vj = vj!!)
                            u
                        }

                        val taggedMap = tagged.associateBy { it.id }
                        synchronized(rawMovies) {
                            for (i in rawMovies.indices) {
                                taggedMap[rawMovies[i].id]?.let { meta ->
                                    if (rawMovies[i].genre.isBlank()) {
                                        rawMovies[i] = rawMovies[i].copy(genre = meta.genre)
                                    }
                                }
                            }
                        }

                        val filteredGenre = if (isVjSpecified) tagged.filter { matchesVj(it, vj) } else tagged
                        val existingIds = synchronized(rawMovies) { rawMovies.map { it.id }.toSet() }
                        val newUnique = filteredGenre.filter { isMovieItem(it) && it.id !in existingIds }
                        if (newUnique.isNotEmpty()) {
                            synchronized(rawMovies) {
                                rawMovies.addAll(newUnique)
                            }
                            MediaCache.putAll(newUnique)
                        }
                    }
                    categoryBatchPage = 3
                    applySortAndFilter()
                }

                // 4. If VJ is specified, fetch from VJ catalog (multiple pages)
                if (isVjSpecified && vjId != null) {
                    val matchedVj = loadedVjCategories.find { it.id == vjId }
                    val pipe = matchedVj?.pipe ?: "genre_vj"

                    for (p in 1..4) {
                        val vjResult = if (isCategorySpecified && genreId != null) {
                            movieRepository.getMoviesByVjAndGenre(vjId, genreId, page = p)
                        } else {
                            movieRepository.getMoviesByVj(vjId, null, pipe, p)
                        }
                        val vjItems = vjResult.getOrDefault(emptyList()).filter { isMovieItem(it) }
                        if (vjItems.isEmpty()) break

                        val filteredVj = if (isCategorySpecified) {
                            vjItems.filter { matchesCategory(it, category) }
                        } else vjItems

                        val tagged = filteredVj.map { item ->
                            var u = if (item.vj.isBlank()) item.copy(vj = vj!!) else item
                            if (isCategorySpecified) {
                                recordItemCategory(u.id, category!!)
                                if (u.genre.isBlank() || u.genre == "-") u = u.copy(genre = category)
                            }
                            u
                        }

                        val taggedMap = tagged.associateBy { it.id }
                        synchronized(rawMovies) {
                            for (i in rawMovies.indices) {
                                taggedMap[rawMovies[i].id]?.let { meta ->
                                    if (rawMovies[i].vj.isBlank()) {
                                        rawMovies[i] = rawMovies[i].copy(vj = meta.vj)
                                    }
                                }
                            }
                        }

                        val existingIds = synchronized(rawMovies) { rawMovies.map { it.id }.toSet() }
                        val newUnique = tagged.filter { it.id !in existingIds }
                        if (newUnique.isNotEmpty()) {
                            synchronized(rawMovies) {
                                rawMovies.addAll(newUnique)
                            }
                            MediaCache.putAll(newUnique)
                        }
                    }
                    vjBatchPage = 4
                    applySortAndFilter()
                }

                // 5. Query Search Discovery terms via searchRepository
                if (searchRepository != null) {
                    val searchQueries: List<String> = when {
                        isVjSpecified && isCategorySpecified -> {
                            val catBlockbusters = CATEGORY_DISCOVERY_QUERIES[category] ?: listOf(category!!)
                            val start = (reshuffleBatchIndex * 4) % catBlockbusters.size
                            val selectedBlockbusters = (0 until minOf(6, catBlockbusters.size)).map {
                                catBlockbusters[(start + it) % catBlockbusters.size]
                            }
                            selectedBlockbusters.flatMap { b ->
                                listOf("$vj $b", b)
                            }
                        }
                        isCategorySpecified -> {
                            val catBlockbusters = CATEGORY_DISCOVERY_QUERIES[category] ?: listOf(category!!)
                            val start = (reshuffleBatchIndex * 5) % catBlockbusters.size
                            (0 until minOf(6, catBlockbusters.size)).map {
                                catBlockbusters[(start + it) % catBlockbusters.size]
                            }
                        }
                        isVjSpecified -> {
                            val allEras = listOf("2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018", "2017", "2016", "2015", "2014", "2012", "2010", "2008", "2005", "2000", "1999")
                            val start = (reshuffleBatchIndex * 4) % allEras.size
                            (0 until 4).map { "$vj ${allEras[(start + it) % allEras.size]}" }
                        }
                        else -> {
                            // General Reshuffle: Sample across ALL eras (1990s, 2000s, 2010s, 2020s)
                            val allEras = listOf("2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018", "2017", "2016", "2015", "2014", "2012", "2010", "2008", "2005", "2000", "1999", "1994")
                            val start = (reshuffleBatchIndex * 4) % allEras.size
                            (0 until 5).map { idx ->
                                val era = allEras[(start + idx) % allEras.size]
                                val queriesForEra = YEAR_DISCOVERY_QUERIES[era] ?: listOf(era)
                                val qIdx = (reshuffleBatchIndex + idx + 1) % queriesForEra.size
                                queriesForEra.getOrNull(qIdx) ?: era
                            }
                        }
                    }

                    val results = withContext(ioDispatcher) {
                        coroutineScope {
                            searchQueries.map { q ->
                                async {
                                    try {
                                        searchRepository.searchContent(q).getOrDefault(emptyList())
                                    } catch (_: Exception) {
                                        emptyList()
                                    }
                                }
                            }.awaitAll().flatten()
                        }
                    }

                    val matching = results.filter { item ->
                        isMovieItem(item) && (!isVjSpecified || matchesVj(item, vj)) &&
                            (!isCategorySpecified || matchesCategory(item, category))
                    }

                    val tagged = matching.map { item ->
                        var updated = item
                        if (isCategorySpecified) {
                            recordItemCategory(updated.id, category!!)
                            if (updated.genre.isBlank() || updated.genre == "-") updated = updated.copy(genre = category)
                        }
                        if (isVjSpecified && updated.vj.isBlank()) {
                            updated = updated.copy(vj = vj!!)
                        }
                        updated
                    }

                    val filteredFinal = if (isCategorySpecified) {
                        tagged.filter { matchesCategory(it, category) || inferCategory(it, category!!) }
                    } else {
                        tagged
                    }

                    val existingIds = synchronized(rawMovies) { rawMovies.map { it.id }.toSet() }
                    val newUnique = filteredFinal.filter { it.id !in existingIds }
                    if (newUnique.isNotEmpty()) {
                        synchronized(rawMovies) {
                            rawMovies.addAll(newUnique)
                        }
                        MediaCache.putAll(newUnique)
                    }
                }
                consecutiveEmptyLoads = 0
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false, canLoadMore = true)
                }
                applySortAndFilter()
            }
        }
    }

    private suspend fun loadMoreForReshuffle(): List<MediaItem> {
        val newlyAdded = mutableListOf<MediaItem>()
        try {
            reshuffleBatchIndex++
            val vj = _uiState.value.selectedVj
            val category = _uiState.value.selectedCategory
            val isVjSpecified = !vj.isNullOrBlank() && !vj.equals("All VJs", ignoreCase = true)
            val isCategorySpecified = !category.isNullOrBlank() && !category.equals("All Categories", ignoreCase = true)
            val genreId = if (isCategorySpecified) findGenreId(category) else null
            val vjId = if (isVjSpecified) findVjId(vj) else null

            // 1. Fetch next page from server catalog only if no category specified
            if (!isCategorySpecified) {
                val catalogPage = (reshuffleBatchIndex % 20) + 2
                val catalogRes = movieRepository.getLatestMovies(null, catalogPage)
                val catalogItems = catalogRes.getOrDefault(emptyList()).filter { isMovieItem(it) }
                if (catalogItems.isNotEmpty()) {
                    val existingIds = synchronized(rawMovies) { rawMovies.map { it.id }.toSet() }
                    val newUnique = catalogItems.filter { isMovieItem(it) && it.id !in existingIds }
                    if (newUnique.isNotEmpty()) {
                        synchronized(rawMovies) {
                            rawMovies.addAll(newUnique)
                        }
                        MediaCache.putAll(newUnique)
                        newlyAdded.addAll(newUnique)
                    }
                }
            }

            // 2. If category specified, fetch next genre page
            if (isCategorySpecified && genreId != null) {
                categoryBatchPage++
                val matchedGenre = loadedGenreCategories.find { it.id == genreId }
                val pipe = matchedGenre?.pipe ?: "genre_vj"
                val res = movieRepository.getMoviesByGenre(genreId, null, pipe, categoryBatchPage)
                val items = res.getOrDefault(emptyList()).filter { isMovieItem(it) }
                if (items.isNotEmpty()) {
                    val tagged = items.map { item ->
                        recordItemCategory(item.id, category!!)
                        var u = item
                        if (isVjSpecified && matchesVj(u, vj) && u.vj.isBlank()) u = u.copy(vj = vj!!)
                        u
                    }
                    val filteredGenre = if (isVjSpecified) tagged.filter { matchesVj(it, vj) } else tagged
                    val existingIds = synchronized(rawMovies) { rawMovies.map { it.id }.toSet() }
                    val newUnique = filteredGenre.filter { isMovieItem(it) && it.id !in existingIds }
                    if (newUnique.isNotEmpty()) {
                        synchronized(rawMovies) {
                            rawMovies.addAll(newUnique)
                        }
                        MediaCache.putAll(newUnique)
                        newlyAdded.addAll(newUnique)
                    }
                }
            }

            // 3. If VJ specified, fetch next VJ page
            if (isVjSpecified && vjId != null) {
                vjBatchPage++
                val res = if (isCategorySpecified && genreId != null) {
                    movieRepository.getMoviesByVjAndGenre(vjId, genreId, page = vjBatchPage)
                } else {
                    val matchedVj = loadedVjCategories.find { it.id == vjId }
                    val pipe = matchedVj?.pipe ?: "genre_vj"
                    movieRepository.getMoviesByVj(vjId, null, pipe, vjBatchPage)
                }
                val items = res.getOrDefault(emptyList()).filter { isMovieItem(it) }
                val filteredItems = items.filter { item ->
                    (!isCategorySpecified || matchesCategory(item, category)) &&
                    (!isVjSpecified || matchesVj(item, vj))
                }
                if (filteredItems.isNotEmpty()) {
                    val tagged = filteredItems.map { item ->
                        var u = if (item.vj.isBlank()) item.copy(vj = vj!!) else item
                        if (isCategorySpecified) {
                            recordItemCategory(u.id, category!!)
                            if (u.genre.isBlank() || u.genre == "-") u = u.copy(genre = category)
                        }
                        u
                    }
                    val existingIds = synchronized(rawMovies) { rawMovies.map { it.id }.toSet() }
                    val newUnique = tagged.filter { it.id !in existingIds }
                    if (newUnique.isNotEmpty()) {
                        synchronized(rawMovies) {
                            rawMovies.addAll(newUnique)
                        }
                        MediaCache.putAll(newUnique)
                        newlyAdded.addAll(newUnique)
                    }
                }
            }

            // 4. Search discovery queries across all years and categories
            if (searchRepository != null) {
                val nextQueries: List<String> = when {
                    isVjSpecified && isCategorySpecified -> {
                        val catQueries = CATEGORY_DISCOVERY_QUERIES[category] ?: listOf(category!!)
                        val start = (reshuffleBatchIndex * 3) % catQueries.size
                        (0 until minOf(4, catQueries.size)).flatMap {
                            val q = catQueries[(start + it) % catQueries.size]
                            listOf("$vj $q", q)
                        }
                    }
                    isCategorySpecified -> {
                        val catQueries = CATEGORY_DISCOVERY_QUERIES[category] ?: listOf(category!!)
                        val start = (reshuffleBatchIndex * 4) % catQueries.size
                        (0 until minOf(4, catQueries.size)).map { catQueries[(start + it) % catQueries.size] }
                    }
                    isVjSpecified -> {
                        val allYears = listOf("2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018", "2017", "2016", "2015", "2014", "2012", "2010", "2008", "2005", "2000", "1999")
                        val start = (reshuffleBatchIndex * 3) % allYears.size
                        (0 until 3).map { "$vj ${allYears[(start + it) % allYears.size]}" }
                    }
                    else -> {
                        // Multi-era queries across ALL years
                        val allEras = listOf("2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018", "2017", "2016", "2015", "2014", "2012", "2010", "2008", "2005", "2000", "1999", "1994")
                        val start = (reshuffleBatchIndex * 4) % allEras.size
                        (0 until 4).map { idx ->
                            val era = allEras[(start + idx) % allEras.size]
                            val qList = YEAR_DISCOVERY_QUERIES[era] ?: listOf(era)
                            val qIdx = (reshuffleBatchIndex + idx + 1) % qList.size
                            qList.getOrNull(qIdx) ?: era
                        }
                    }
                }

                val results = withContext(ioDispatcher) {
                    coroutineScope {
                        nextQueries.map { q ->
                            async {
                                try {
                                    searchRepository.searchContent(q).getOrDefault(emptyList())
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                        }.awaitAll().flatten()
                    }
                }

                val matching = results.filter { item ->
                    isMovieItem(item) && (!isVjSpecified || matchesVj(item, vj)) &&
                        (!isCategorySpecified || matchesCategory(item, category))
                }
                val tagged = matching.map { item ->
                    var u = item
                    if (isCategorySpecified) {
                        recordItemCategory(u.id, category!!)
                        if (u.genre.isBlank() || u.genre == "-") u = u.copy(genre = category)
                    }
                    if (isVjSpecified && u.vj.isBlank()) {
                        u = u.copy(vj = vj!!)
                    }
                    u
                }
                val filteredFinal = tagged

                val existingIds = synchronized(rawMovies) { rawMovies.map { it.id }.toSet() }
                val newUnique = filteredFinal.filter { isMovieItem(it) && it.id !in existingIds }
                if (newUnique.isNotEmpty()) {
                    synchronized(rawMovies) {
                        rawMovies.addAll(newUnique)
                    }
                    MediaCache.putAll(newUnique)
                    newlyAdded.addAll(newUnique)
                }
            }
        } catch (_: Exception) {}
        return newlyAdded
    }

    private fun fetchAdditionalMoviesForVj(vj: String) {
        vjFetchJob?.cancel()
        vjFetchJob = viewModelScope.launch(ioDispatcher) {
            try {
                // 1. Check local MediaCache for any movies for this VJ
                val cachedVj = MediaCache.getAll().filter { isMovieItem(it) && matchesVj(it, vj) }
                if (cachedVj.isNotEmpty()) {
                    val existingIds = synchronized(rawMovies) { rawMovies.map { it.id }.toSet() }
                    val newUnique = cachedVj.filter { isMovieItem(it) && it.id !in existingIds }
                    if (newUnique.isNotEmpty()) {
                        synchronized(rawMovies) {
                            rawMovies.addAll(newUnique)
                        }
                        applySortAndFilter()
                    }
                }

                // 2. Query searchRepository for this VJ
                if (searchRepository != null) {
                    val res = searchRepository.searchContent(vj)
                    val items = res.getOrDefault(emptyList())
                    val vjMovies = items.filter { isMovieItem(it) && matchesVj(it, vj) }
                    if (vjMovies.isNotEmpty()) {
                        val existingIds = synchronized(rawMovies) { rawMovies.map { it.id }.toSet() }
                        val newUnique = vjMovies.filter { isMovieItem(it) && it.id !in existingIds }
                        if (newUnique.isNotEmpty()) {
                            synchronized(rawMovies) {
                                rawMovies.addAll(newUnique)
                            }
                            MediaCache.putAll(newUnique)
                            applySortAndFilter()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun loadMovies(isFree: Boolean = false, forceReload: Boolean = false) {
        loadJob?.cancel()
        lastFetchId = null
        currentPage = 1

        val cachedForMode = if (isFree) MediaCache.getFreeMovies() else MediaCache.getPopularMovies()

        if (forceReload || (cachedForMode.isEmpty() && synchronized(rawMovies) { rawMovies.isEmpty() })) {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                isFilterFree = isFree,
                errorMessage = null,
                canLoadMore = true,
                isLoadingMore = false
            )
        } else {
            if (cachedForMode.isNotEmpty()) {
                synchronized(rawMovies) {
                    rawMovies.clear()
                    rawMovies.addAll(cachedForMode)
                }
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isFilterFree = isFree,
                errorMessage = null,
                canLoadMore = true,
                isLoadingMore = false
            )
            applySortAndFilter()
        }

        loadJob = viewModelScope.launch(ioDispatcher) {
            val result = if (isFree) movieRepository.getFreeMovies(null, 1) else movieRepository.getLatestMovies(null, 1)
            result.onSuccess { list ->
                if (_uiState.value.selectedYear != null || _uiState.value.selectedCategory != null) return@onSuccess
                if (list.isNotEmpty()) {
                    if (isFree) MediaCache.setFreeMovies(list) else MediaCache.setPopularMovies(list)
                    synchronized(rawMovies) {
                        rawMovies.clear()
                        rawMovies.addAll(list)
                    }
                }
                lastFetchId = list.lastOrNull()?.id
                currentPage = 1
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        canLoadMore = list.isNotEmpty()
                    )
                }
                applySortAndFilter()

                // Background preload multiple batches so user immediately sees a deep catalog of movies
                val accumulated = synchronized(rawMovies) { rawMovies.toMutableList() }
                var currentLastId = lastFetchId

                for (pageIndex in 2..4) {
                    if (_uiState.value.selectedYear != null || _uiState.value.selectedCategory != null) break
                    if (currentLastId == null) break
                    kotlinx.coroutines.delay(400)
                    val nextRes = if (isFree) movieRepository.getFreeMovies(currentLastId, pageIndex) else movieRepository.getLatestMovies(currentLastId, pageIndex)
                    val nextList = nextRes.getOrDefault(emptyList())
                    if (nextList.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(canLoadMore = false)
                        }
                        break
                    }
                    val newUnique = nextList.filter { item -> accumulated.none { it.id == item.id } }
                    if (newUnique.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(canLoadMore = false)
                        }
                        break
                    }
                    accumulated.addAll(newUnique)
                    if (isFree) MediaCache.setFreeMovies(accumulated) else MediaCache.setPopularMovies(accumulated)
                    currentLastId = newUnique.lastOrNull()?.id ?: nextList.last().id
                    lastFetchId = currentLastId
                    currentPage = pageIndex
                    synchronized(rawMovies) {
                        rawMovies.clear()
                        rawMovies.addAll(accumulated)
                    }
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(canLoadMore = true)
                    }
                    applySortAndFilter()
                }
            }.onFailure {
                val currentMovies = _uiState.value.movies
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = if (currentMovies.isEmpty()) "Unable to load movies. Please check your network connection." else null
                    )
                }
            }
        }
    }

    fun loadMore() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || currentState.isLoading || isLoadingMoreActive) return
        isLoadingMoreActive = true
        _uiState.value = _uiState.value.copy(isLoadingMore = true)

        viewModelScope.launch(ioDispatcher) {
            try {
                // 1. Pagination when in Old Movies mode
                if (currentState.sortMode == MovieSortMode.OLD_MOVIES) {
                    var newItems = loadMoreOldMovies()
                    var filteredNew = newItems.filter { isOldMovie(it) }
                    var attempts = 0
                    while (filteredNew.isEmpty() && attempts < 2) {
                        attempts++
                        newItems = loadMoreOldMovies()
                        filteredNew = newItems.filter { isOldMovie(it) }
                    }
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isLoadingMore = false,
                            canLoadMore = true
                        )
                        appendLoadedMoviesToBottom(filteredNew)
                    }
                    return@launch
                }

                val hasFilter = currentState.selectedYear != null ||
                    (!currentState.selectedCategory.isNullOrBlank() && !currentState.selectedCategory.equals("All Categories", ignoreCase = true) && !currentState.selectedCategory.equals("All Genres", ignoreCase = true)) ||
                    (!currentState.selectedVj.isNullOrBlank() && !currentState.selectedVj.equals("All VJs", ignoreCase = true))

                if (hasFilter && currentState.sortMode != MovieSortMode.RANDOM) {
                    fetchFilteredMovies(resetPage = false)
                    return@launch
                }

                // 2. Pagination when in Reshuffle mode (load more movies across all years / categories / VJs)
                if (currentState.sortMode == MovieSortMode.RANDOM) {
                    val newItems = loadMoreForReshuffle()
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isLoadingMore = false,
                            canLoadMore = true
                        )
                        appendLoadedMoviesToBottom(newItems.shuffled())
                    }
                    return@launch
                }

                // 3. Standard Latest / Free pagination
                val currentFetchId = synchronized(rawMovies) { lastFetchId ?: rawMovies.lastOrNull()?.id }
                if (currentFetchId == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(isLoadingMore = false)
                    }
                    return@launch
                }
                val nextPage = currentPage + 1
                val result = if (currentState.isFilterFree) {
                    movieRepository.getFreeMovies(currentFetchId, nextPage)
                } else {
                    movieRepository.getLatestMovies(currentFetchId, nextPage)
                }

                val nextList = result.getOrDefault(emptyList())
                if (nextList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(isLoadingMore = false, canLoadMore = true)
                    }
                } else {
                    val newUnique = synchronized(rawMovies) {
                        nextList.filter { item -> isMovieItem(item) && rawMovies.none { it.id == item.id } }
                    }
                    if (newUnique.isNotEmpty()) {
                        lastFetchId = newUnique.lastOrNull()?.id ?: nextList.last().id
                        currentPage = nextPage
                        synchronized(rawMovies) {
                            rawMovies.addAll(newUnique)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isLoadingMore = false,
                            canLoadMore = true
                        )
                        appendLoadedMoviesToBottom(newUnique)
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                }
            } finally {
                isLoadingMoreActive = false
            }
        }
    }
}

@Composable
fun MoviesScreen(
    viewModel: MoviesViewModel,
    onNavigate: (String) -> Unit,
    onOpenDetails: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val gridState = rememberSaveable(
        saver = LazyGridState.Saver
    ) {
        LazyGridState(
            firstVisibleItemIndex = viewModel.scrollIndex,
            firstVisibleItemScrollOffset = viewModel.scrollOffset
        )
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (idx, offset) ->
                viewModel.scrollIndex = idx
                viewModel.scrollOffset = offset
            }
    }

    var lastFilterKey by rememberSaveable { mutableStateOf<String?>(null) }
    val currentFilterKey = "${state.sortMode}_${state.selectedYear}_${state.selectedCategory}_${state.selectedVj}"

    LaunchedEffect(currentFilterKey) {
        if (lastFilterKey != null && lastFilterKey != currentFilterKey) {
            viewModel.scrollIndex = 0
            viewModel.scrollOffset = 0
            try {
                gridState.scrollToItem(0)
            } catch (_: Exception) {}
        }
        lastFilterKey = currentFilterKey
    }

    LaunchedEffect(state.movies.size) {
        if (viewModel.scrollIndex > 0 && state.movies.size > viewModel.scrollIndex) {
            if (gridState.firstVisibleItemIndex == 0) {
                try {
                    gridState.scrollToItem(viewModel.scrollIndex, viewModel.scrollOffset)
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible to total
        }
        .distinctUntilChanged()
        .collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 2) {
                val s = viewModel.uiState.value
                if (!s.isLoadingMore && !s.isLoading && s.canLoadMore && s.movies.isNotEmpty()) {
                    viewModel.loadMore()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MunowatchBackground)
    ) {
        TvTopBar(
            currentRoute = "movies",
            onNavigate = onNavigate
        )

        // Filters bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MOVIES CATALOG",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                if (state.movies.isNotEmpty()) {
                    val subtitle = when {
                        state.sortMode == MovieSortMode.RANDOM && state.selectedCategory != null && state.selectedVj != null && state.selectedYear != null ->
                            "${state.movies.size} movies • Reshuffle ${state.selectedVj} in ${state.selectedCategory} (${state.selectedYear})"
                        state.sortMode == MovieSortMode.RANDOM && state.selectedCategory != null && state.selectedVj != null ->
                            "${state.movies.size} movies • Reshuffle ${state.selectedVj} (${state.selectedCategory})"
                        state.sortMode == MovieSortMode.RANDOM && state.selectedCategory != null ->
                            "${state.movies.size} movies • Reshuffle ${state.selectedCategory}"
                        state.sortMode == MovieSortMode.RANDOM && state.selectedVj != null && state.selectedYear != null ->
                            "${state.movies.size} movies • Reshuffle ${state.selectedVj} (${state.selectedYear})"
                        state.sortMode == MovieSortMode.RANDOM && state.selectedVj != null ->
                            "${state.movies.size} movies • Reshuffle ${state.selectedVj}"
                        state.sortMode == MovieSortMode.RANDOM && state.selectedYear != null ->
                            "${state.movies.size} movies • Year ${state.selectedYear} (Random order)"
                        state.sortMode == MovieSortMode.RANDOM ->
                            "${state.movies.size} movies • Shuffled randomly"
                        state.sortMode == MovieSortMode.OLD_MOVIES && state.selectedCategory != null && state.selectedVj != null ->
                            "${state.movies.size} old movies • ${state.selectedVj} (${state.selectedCategory})"
                        state.sortMode == MovieSortMode.OLD_MOVIES && state.selectedCategory != null ->
                            "${state.movies.size} old movies • ${state.selectedCategory}"
                        state.sortMode == MovieSortMode.OLD_MOVIES && state.selectedVj != null ->
                            "${state.movies.size} old movies • ${state.selectedVj}"
                        state.sortMode == MovieSortMode.OLD_MOVIES ->
                            "${state.movies.size} classic & old movies (2025 & earlier)"
                        state.selectedCategory != null && state.selectedVj != null ->
                            "${state.movies.size} movies • ${state.selectedVj} (${state.selectedCategory})"
                        state.selectedCategory != null ->
                            "${state.movies.size} movies • ${state.selectedCategory}"
                        state.selectedYear != null && state.selectedVj != null ->
                            "${state.movies.size} movies • ${state.selectedVj} in ${state.selectedYear}"
                        state.selectedYear != null ->
                            "${state.movies.size} movies from ${state.selectedYear}"
                        state.selectedVj != null ->
                            "${state.movies.size} movies • ${state.selectedVj}"
                        state.sortMode == MovieSortMode.BY_YEAR ->
                            "${state.movies.size} movies • Sorted by upload time"
                        else ->
                            "${state.movies.size} movies loaded${if (state.canLoadMore) " • Scroll to browse more" else ""}"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MunowatchTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (state.isLoadingMore) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MunowatchGreen,
                    strokeWidth = 2.dp
                )
            }

            TvButton(
                text = "All Latest",
                onClick = { viewModel.setSortLatest() },
                isPrimary = (state.sortMode == MovieSortMode.LATEST && state.selectedYear == null && state.selectedVj == null && state.selectedCategory == null)
            )
            TvButton(
                text = if (state.sortMode == MovieSortMode.RANDOM) "🔀 Reshuffle" else "🔀 Random",
                onClick = { viewModel.sortRandomly() },
                isPrimary = (state.sortMode == MovieSortMode.RANDOM)
            )
            TvButton(
                text = "📼 Old Movies",
                onClick = { viewModel.toggleOldMovies() },
                isPrimary = (state.sortMode == MovieSortMode.OLD_MOVIES)
            )
        }

        // Reshuffle Category & VJ Selector rows when in RANDOM mode
        if (state.sortMode == MovieSortMode.RANDOM) {
            // Category selector row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "RESHUFFLE CATEGORY:",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MunowatchGreenLight,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(end = 36.dp)
                ) {
                    item {
                        TvButton(
                            text = "All Categories",
                            onClick = { viewModel.selectCategory(null) },
                            isPrimary = (state.selectedCategory == null || state.selectedCategory == "All Categories"),
                            isCompact = true
                        )
                    }
                    items(state.availableCategories.filter { !it.equals("All Categories", ignoreCase = true) }) { category ->
                        TvButton(
                            text = category,
                            onClick = { viewModel.selectCategory(category) },
                            isPrimary = (state.selectedCategory == category),
                            isCompact = true
                        )
                    }
                }
            }

            // VJ selector row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "RESHUFFLE VJ:",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MunowatchGreenLight,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(end = 36.dp)
                ) {
                    item {
                        TvButton(
                            text = "All VJs",
                            onClick = { viewModel.selectVj(null) },
                            isPrimary = (state.selectedVj == null || state.selectedVj == "All VJs"),
                            isCompact = true
                        )
                    }
                    items(state.availableVjs.filter { !it.equals("All VJs", ignoreCase = true) }) { vj ->
                        TvButton(
                            text = vj,
                            onClick = { viewModel.selectVj(vj) },
                            isPrimary = (state.selectedVj == vj),
                            isCompact = true
                        )
                    }
                }
            }
        }

        // Old Movies Genre & VJ Filters (or active filters in non-random mode)
        if (state.sortMode == MovieSortMode.OLD_MOVIES || (state.sortMode != MovieSortMode.RANDOM && (state.selectedCategory != null || state.selectedVj != null))) {
            // Genre Filter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "FILTER GENRE:",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MunowatchGreenLight,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(end = 36.dp)
                ) {
                    item {
                        TvButton(
                            text = "All Genres",
                            onClick = { viewModel.selectCategory(null) },
                            isPrimary = (state.selectedCategory == null || state.selectedCategory == "All Categories" || state.selectedCategory == "All Genres"),
                            isCompact = true
                        )
                    }
                    items(state.availableCategories.filter { !it.equals("All Categories", ignoreCase = true) && !it.equals("All Genres", ignoreCase = true) }) { category ->
                        TvButton(
                            text = category,
                            onClick = { viewModel.selectCategory(category) },
                            isPrimary = (state.selectedCategory == category),
                            isCompact = true
                        )
                    }
                }
            }

            // VJ Filter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "FILTER VJ:",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MunowatchGreenLight,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(end = 36.dp)
                ) {
                    item {
                        TvButton(
                            text = "All VJs",
                            onClick = { viewModel.selectVj(null) },
                            isPrimary = (state.selectedVj == null || state.selectedVj == "All VJs"),
                            isCompact = true
                        )
                    }
                    items(state.availableVjs.filter { !it.equals("All VJs", ignoreCase = true) }) { vj ->
                        TvButton(
                            text = vj,
                            onClick = { viewModel.selectVj(vj) },
                            isPrimary = (state.selectedVj == vj),
                            isCompact = true
                        )
                    }
                }
            }
        }

        // Action / Status bar directly under categories & genres
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val activeCategoryDesc = when {
                state.sortMode == MovieSortMode.OLD_MOVIES && state.selectedCategory != null && state.selectedVj != null ->
                    "📼 Old Movies • ${state.selectedVj} in ${state.selectedCategory}"
                state.sortMode == MovieSortMode.OLD_MOVIES && state.selectedCategory != null ->
                    "📼 Old Movies • ${state.selectedCategory}"
                state.sortMode == MovieSortMode.OLD_MOVIES && state.selectedVj != null ->
                    "📼 Old Movies • ${state.selectedVj}"
                state.sortMode == MovieSortMode.OLD_MOVIES ->
                    "📼 Old Movies (2025 & earlier)"
                state.selectedCategory != null && state.selectedVj != null ->
                    "${state.selectedVj} (${state.selectedCategory})"
                state.selectedCategory != null ->
                    "${state.selectedCategory} Movies"
                state.selectedVj != null ->
                    "${state.selectedVj} Catalog"
                state.sortMode == MovieSortMode.RANDOM ->
                    "Random Shuffled"
                else ->
                    "All Latest Releases"
            }

            Text(
                text = "${state.movies.size} movies loaded • $activeCategoryDesc",
                style = MaterialTheme.typography.bodySmall,
                color = MunowatchTextSecondary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (state.isLoading) {
            TvLoadingIndicator(message = "Loading Movie Catalog...")
        } else if (state.errorMessage != null && state.movies.isEmpty()) {
            TvErrorView(
                message = state.errorMessage ?: "",
                onRetry = { viewModel.loadMovies(state.isFilterFree) }
            )
        } else if (state.movies.isEmpty()) {
            if (state.isLoadingMore) {
                TvLoadingIndicator(message = "Searching for movies...")
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val emptyMessage = when {
                            state.sortMode == MovieSortMode.OLD_MOVIES && state.selectedCategory != null && state.selectedVj != null ->
                                "No old movies found for ${state.selectedVj} in ${state.selectedCategory}"
                            state.sortMode == MovieSortMode.OLD_MOVIES && state.selectedCategory != null ->
                                "No old ${state.selectedCategory} movies found"
                            state.sortMode == MovieSortMode.OLD_MOVIES && state.selectedVj != null ->
                                "No old movies found for ${state.selectedVj}"
                            state.sortMode == MovieSortMode.OLD_MOVIES ->
                                "No old movies found (2025 & earlier)"
                            state.selectedCategory != null && state.selectedVj != null && state.selectedYear != null ->
                                "No movies found for ${state.selectedVj} in ${state.selectedCategory} (${state.selectedYear})"
                            state.selectedCategory != null && state.selectedVj != null ->
                                "No ${state.selectedCategory} movies found for ${state.selectedVj}"
                            state.selectedCategory != null ->
                                "No ${state.selectedCategory} movies found"
                            state.selectedYear != null && state.selectedVj != null ->
                                "No movies found for ${state.selectedVj} in year ${state.selectedYear}"
                            state.selectedYear != null ->
                                "No movies found for year ${state.selectedYear}"
                            state.selectedVj != null ->
                                "No movies found for ${state.selectedVj}"
                            else ->
                                "No movies available"
                        }
                        Text(
                            text = emptyMessage,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvButton(
                                text = if (state.isLoadingMore) "⏳ Loading More Movies..." else "Load More Movies",
                                onClick = { viewModel.loadMore() },
                                isPrimary = true
                            )
                            TvButton(
                                text = "Show All Movies",
                                onClick = { viewModel.setSortLatest() }
                            )
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 125.dp),
                contentPadding = PaddingValues(start = 36.dp, end = 36.dp, bottom = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = state.movies,
                    key = { movie -> movie.id.ifBlank { movie.streamUrl }.ifBlank { movie.title } }
                ) { movie ->
                    TvMediaCard(
                        item = movie,
                        onClick = {
                            viewModel.scrollIndex = gridState.firstVisibleItemIndex
                            viewModel.scrollOffset = gridState.firstVisibleItemScrollOffset
                            viewModel.lastOpenedMovieId = movie.id
                            onOpenDetails(movie.id)
                        }
                    )
                }

                if (state.movies.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TvButton(
                                text = if (state.isLoadingMore) "⏳ Loading More Movies..." else "Load More Movies",
                                onClick = {
                                    if (!state.isLoadingMore) {
                                        viewModel.loadMore()
                                    }
                                },
                                isPrimary = true
                            )
                        }
                    }
                }
            }
        }
    }
}
