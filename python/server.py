import datetime
import functools
import gzip
import json
import os
import re
from typing import List, Optional, TypeVar, Callable

from flask import Flask, request, jsonify

import ddtrace.profiling.auto


I = TypeVar('I')
O = TypeVar('O')


def convert_or_none(v: Optional[I], converter: Callable[[I], O]) -> Optional[O]:
    if v is None:
        return v

    return converter(v)


class Movie:
    def __init__(self, d: dict):
        self.__d = d

    @property
    def title(self) -> Optional[str]:
        return convert_or_none(self.__d.get("title"), str)

    @property
    def rating(self) -> Optional[float]:
        return convert_or_none(self.__d.get("vote_average"), float)

    @property
    def release_date(self) -> Optional[str]:
        return convert_or_none(self.__d.get("release_date"), str)

    def to_dict(self):
        return {
            "title": self.title,
            "rating": self.rating,
            "release_date": self.release_date,
        }


SERVER_DIR = os.path.dirname(os.path.realpath(__file__))
CACHED_MOVIES: Optional[List[Movie]] = None

app = Flask(__name__)


def main():
    app.run(host="0.0.0.0", port=8080, debug=False, threaded=True)


@app.route('/movies')
def movies():
    query: str = request.args.get("q", request.args.get("query"))

    movies = get_movies()

    # Problem: We are sorting over the entire list but might be filtering most of it out later.
    # Solution: Sort after filtering
    movies = sort_desc_releasedate(movies)

    if query:
        movies = [m for m in movies if re.search(query.upper(), m.title.upper())]

    return jsonify([m.to_dict() for m in movies])


def sort_desc_releasedate(movies: List[Movie]) -> List[Movie]:
    # Problem: We are parsing a datetime for each comparison during sort
    # Example Solution:
    #   Since date is in isoformat (yyyy-mm-dd) already, that one sorts nicely with normal string sorting
    #   `return sorted(movies, key=lambda m: m.release_date, reverse=True)`
    def sorting_cmp(m1: Movie, m2: Movie) -> int:
        try:
            m1_dt = datetime.date.fromisoformat(m1.release_date)
        except Exception:
            m1_dt = datetime.date.min
        try:
            m2_dt = datetime.date.fromisoformat(m2.release_date)
        except Exception:
            m2_dt = datetime.date.min
        return int((m1_dt - m2_dt).total_seconds())

    return sorted(movies, key=functools.cmp_to_key(sorting_cmp), reverse=True)


def get_movies() -> List[Movie]:
    global CACHED_MOVIES

    if CACHED_MOVIES:
        return CACHED_MOVIES

    return load_movies()


def load_movies():
    global CACHED_MOVIES
    with gzip.open(os.path.join(SERVER_DIR, "../movies5000.json.gz")) as f:
        CACHED_MOVIES = [Movie(d) for d in json.load(f)]
        return CACHED_MOVIES


if __name__ == '__main__':
    main()
