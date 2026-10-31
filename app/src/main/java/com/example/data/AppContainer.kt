package com.example.data

import android.content.Context
import com.example.data.api.MunowatchApi
import com.example.data.api.MunowatchHtmlParser
import com.example.data.local.AppDatabase
import com.example.data.network.NetworkClient
import com.example.data.repository.AppUpdateRepositoryImpl
import com.example.data.repository.AuthenticationRepositoryImpl
import com.example.data.repository.MovieRepositoryImpl
import com.example.data.repository.MunowatchRepositoryImpl
import com.example.data.repository.PlaybackRepositoryImpl
import com.example.data.repository.SearchRepositoryImpl
import com.example.data.repository.SeriesRepositoryImpl
import com.example.data.session.SecureSessionManager
import com.example.domain.repository.AppUpdateRepository
import com.example.domain.repository.AuthenticationRepository
import com.example.domain.repository.MovieRepository
import com.example.domain.repository.MunowatchRepository
import com.example.domain.repository.PlaybackRepository
import com.example.domain.repository.SearchRepository
import com.example.data.repository.MediaCache
import com.example.domain.repository.SeriesRepository

class AppContainer(context: Context) {
    init {
        MediaCache.init(context)
    }

    val database = AppDatabase.getDatabase(context)
    val sessionManager = SecureSessionManager(context)
    val networkClient = NetworkClient(sessionManager)
    val api = MunowatchApi(networkClient, sessionManager)
    val htmlParser = MunowatchHtmlParser()

    val authRepository: AuthenticationRepository = AuthenticationRepositoryImpl(sessionManager, api)
    val movieRepository: MovieRepository = MovieRepositoryImpl(api, htmlParser)
    val seriesRepository: SeriesRepository = SeriesRepositoryImpl(api, htmlParser)
    val searchRepository: SearchRepository = SearchRepositoryImpl(api, htmlParser)
    val playbackRepository: PlaybackRepository = PlaybackRepositoryImpl(api, htmlParser, database, sessionManager)
    val munowatchRepository: MunowatchRepository = MunowatchRepositoryImpl(context, api, sessionManager, database, htmlParser)
    val appUpdateRepository: AppUpdateRepository = AppUpdateRepositoryImpl(context, networkClient.okHttpClient)
}
