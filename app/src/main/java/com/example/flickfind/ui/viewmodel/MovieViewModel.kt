package com.example.flickfind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.flickfind.data.local.MovieEntity
import com.example.flickfind.data.local.UserEntity
import com.example.flickfind.data.local.ReviewEntity
import com.example.flickfind.data.model.Movie
import com.example.flickfind.data.repository.MovieRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MovieViewModel(private val repository: MovieRepository) : ViewModel() {

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _movieReviews = MutableStateFlow<List<ReviewEntity>>(emptyList())
    val movieReviews: StateFlow<List<ReviewEntity>> = _movieReviews.asStateFlow()

    private val _movieAverageRating = MutableStateFlow<Double?>(null)
    val movieAverageRating: StateFlow<Double?> = _movieAverageRating.asStateFlow()

    private var reviewsJob: kotlinx.coroutines.Job? = null
    private var avgRatingJob: kotlinx.coroutines.Job? = null

    private val _nowPlayingMovies = MutableStateFlow<List<Movie>>(emptyList())
    val nowPlayingMovies: StateFlow<List<Movie>> = _nowPlayingMovies.asStateFlow()

    private val _popularMovies = MutableStateFlow<List<Movie>>(emptyList())
    val popularMovies: StateFlow<List<Movie>> = _popularMovies.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Movie>>(emptyList())
    val searchResults: StateFlow<List<Movie>> = _searchResults.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val watchlist: StateFlow<List<MovieEntity>> = repository.watchlist
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isLoadingNowPlaying = MutableStateFlow(false)
    val isLoadingNowPlaying: StateFlow<Boolean> = _isLoadingNowPlaying.asStateFlow()

    private val _isLoadingPopular = MutableStateFlow(false)
    val isLoadingPopular: StateFlow<Boolean> = _isLoadingPopular.asStateFlow()

    private val _selectedMovie = MutableStateFlow<Movie?>(null)
    val selectedMovie: StateFlow<Movie?> = _selectedMovie.asStateFlow()

    private val _isLoadingDetail = MutableStateFlow(false)
    val isLoadingDetail: StateFlow<Boolean> = _isLoadingDetail.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        fetchNowPlaying()
        fetchPopular()
    }

    fun fetchNowPlaying() {
        viewModelScope.launch {
            _isLoadingNowPlaying.value = true
            _errorMessage.value = null
            try {
                val results = repository.getNowPlaying()
                _nowPlayingMovies.value = results
            } catch (e: Exception) {
                android.util.Log.e("MovieViewModel", "Error fetching now playing: ${e.message}", e)
                _errorMessage.value = "Lỗi kết nối: ${e.message}"
                _nowPlayingMovies.value = emptyList()
            } finally {
                _isLoadingNowPlaying.value = false
            }
        }
    }

    fun fetchPopular() {
        viewModelScope.launch {
            _isLoadingPopular.value = true
            _errorMessage.value = null
            try {
                val results = repository.getPopular()
                _popularMovies.value = results
            } catch (e: Exception) {
                android.util.Log.e("MovieViewModel", "Error fetching popular: ${e.message}", e)
                _errorMessage.value = "Lỗi kết nối: ${e.message}"
                _popularMovies.value = emptyList()
            } finally {
                _isLoadingPopular.value = false
            }
        }
    }

    fun searchMovies(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _searchResults.value = emptyList()
            } else {
                _searchResults.value = repository.searchMovies(query)
            }
        }
    }

    fun toggleWatchlist(movie: Movie) {
        viewModelScope.launch {
            val isFavorite = watchlist.value.any { it.id == movie.id }
            if (isFavorite) {
                val entity = watchlist.value.find { it.id == movie.id }
                entity?.let { repository.removeFromWatchlist(it) }
            } else {
                repository.addToWatchlist(movie)
            }
        }
    }

    fun removeFromWatchlist(movie: MovieEntity) {
        viewModelScope.launch {
            repository.removeFromWatchlist(movie)
        }
    }

    fun fetchReviewsForMovie(movieId: Int) {
        reviewsJob?.cancel()
        reviewsJob = viewModelScope.launch {
            repository.getReviewsForMovie(movieId).collect {
                _movieReviews.value = it
            }
        }

        avgRatingJob?.cancel()
        avgRatingJob = viewModelScope.launch {
            repository.getAverageRatingForMovie(movieId).collect {
                _movieAverageRating.value = it
            }
        }
    }

    fun fetchMovieDetails(movieId: Int) {
        viewModelScope.launch {
            _isLoadingDetail.value = true
            _errorMessage.value = null
            try {
                val movie = repository.getMovieDetails(movieId)
                if (movie != null && (movie.overview.isNullOrBlank())) {
                    // Nếu không có mô tả tiếng Việt, thử lấy bản tiếng Anh
                    val englishMovie = repository.getMovieDetails(movieId, "en-US")
                    _selectedMovie.value = movie.copy(overview = englishMovie?.overview)
                } else {
                    _selectedMovie.value = movie
                }
                // Tải đánh giá & bình luận cho phim
                fetchReviewsForMovie(movieId)
            } catch (e: Exception) {
                android.util.Log.e("MovieViewModel", "Error fetching movie details: ${e.message}", e)
                _errorMessage.value = "Không thể tải chi tiết phim: ${e.message}"
            } finally {
                _isLoadingDetail.value = false
            }
        }
    }

    fun clearSelectedMovie() {
        _selectedMovie.value = null
        reviewsJob?.cancel()
        avgRatingJob?.cancel()
        _movieReviews.value = emptyList()
        _movieAverageRating.value = null
    }

    suspend fun register(username: String, passwordHash: String, displayName: String): Boolean {
        val newUser = UserEntity(username, passwordHash, displayName)
        val success = repository.registerUser(newUser)
        if (success) {
            _currentUser.value = newUser
        }
        return success
    }

    suspend fun login(username: String, passwordHash: String): Boolean {
        val user = repository.loginUser(username, passwordHash)
        return if (user != null) {
            _currentUser.value = user
            true
        } else {
            false
        }
    }

    fun logout() {
        _currentUser.value = null
    }

    fun submitReview(movieId: Int, rating: Int, comment: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val newReview = ReviewEntity(
                movieId = movieId,
                username = user.username,
                displayName = user.displayName,
                rating = rating,
                comment = comment,
                timestamp = System.currentTimeMillis()
            )
            repository.addReview(newReview)
        }
    }
}

class MovieViewModelFactory(private val repository: MovieRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MovieViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MovieViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
