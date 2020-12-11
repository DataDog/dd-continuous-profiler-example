package movies;

import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.Spark.port;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import datadog.trace.api.Trace;
import spark.Request;
import spark.Response;

public class Server {
	private static final Gson GSON;
	private static volatile List<Movie> CACHED_MOVIES;

	static {
		GSON = new GsonBuilder().setLenient().create();
	}

	public static void main(String[] args) {
		port(8080);
		get("/movies", Server::moviesEndpoint);

		exception(Exception.class, (exception, request, response) -> {
			System.err.println(exception.getMessage());
			exception.printStackTrace();
		});
	}

	@Trace(operationName = "http.req", resourceName = "/movies")
	private static Object moviesEndpoint(Request req, Response res) {
		var movies = getMovies().stream();
		movies = sortByDescReleaseDate(movies);
		var query = req.queryParamOrDefault("q", req.queryParams("query"));
		if (query != null) {
			// Problem: We are not compiling the pattern and there's a more efficient way of ignoring cases.
			// Solution:
			//   var p = Pattern.compile(query, Pattern.CASE_INSENSITIVE);
			//   movies = movies.filter(m -> p.matcher(m.title).find());
			movies = movies.filter(m -> Pattern.matches(".*" + query.toUpperCase() + ".*", m.title.toUpperCase()));
		}
		return replyJSON(res, movies);
	}

	private static Stream<Movie> sortByDescReleaseDate(Stream<Movie> movies) {
		return movies.sorted(Comparator.comparing((Movie m) -> {
			// Problem: We are parsing a datetime for each item to be sorted.
			// Example Solution:
			//   Since date is in isoformat (yyyy-mm-dd) already, that one sorts nicely with normal string sorting
			//   `return m.releaseDate`
			try {
				return LocalDate.parse(m.releaseDate);
			} catch (Exception e) {
				return LocalDate.MIN;
			}
		}).reversed());
	}

	private static Object replyJSON(Response res, Stream<?> data) {
		return replyJSON(res, data.collect(Collectors.toList()));
	}

	private static Object replyJSON(Response res, Object data) {
		res.type("application/json");
		return GSON.toJson(data);
	}

	private static List<Movie> getMovies() {
		if (CACHED_MOVIES != null) {
			return CACHED_MOVIES;
		}

		return loadMovies();
	}

	private synchronized static List<Movie> loadMovies() {
		if (CACHED_MOVIES != null) {
			return CACHED_MOVIES;
		}

		try (
				var is = ClassLoader.getSystemResourceAsStream("movies5000.json.gz");
				var gzis = new GZIPInputStream(is);
				var reader = new InputStreamReader(gzis)
		){
			return CACHED_MOVIES = GSON.fromJson(reader, new TypeToken<List<Movie>>() {}.getType());
		} catch (IOException e) {
			throw new RuntimeException("Failed to load movie data");
		}
	}

	public static class Movie {
		public String title;
		@SerializedName("vote_average")
		public double rating;
		@SerializedName("release_date")
		public String releaseDate;
	}
}
