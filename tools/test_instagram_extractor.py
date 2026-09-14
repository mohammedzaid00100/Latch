#!/usr/bin/env python3
"""Exercise the real bundled Instagram extractor with synthetic HTTP responses.

No live network requests, credentials, social media, or downloaded third-party
videos are involved. This targets the Android-compatible webpage fallback.
"""
import json
import pathlib
import sys
import unittest
from types import SimpleNamespace
from unittest.mock import Mock

root = pathlib.Path(__file__).resolve().parent.parent
binary = root / "app/build/generated/extractorAssets/engine/yt-dlp"
sys.path.insert(0, str(binary))
from yt_dlp import YoutubeDL
from yt_dlp.extractor.instagram import InstagramIE
from yt_dlp.utils import ExtractorError
from yt_dlp.version import __version__

URL = "https://www.instagram.com/reel/AbC123/"
CDN_URL = "https://cdn.example.com/latch-synthetic.mp4"


def page(product):
    # This is the public-page shape read by yt-dlp's current SJS fallback.
    media = {"if_not_gated_logged_out": product}
    cache = {"__bbox": {"result": {"data": {"xig_polaris_media": media}}}}
    relay = ["RelayPrefetchedStreamCache", "next", [], [cache]]
    outer = {"__bbox": {"require": [relay]}}
    data = {"require": [["ScheduledServerJS", "handle", [], [outer]]]}
    return '<html><script type="application/json" data-sjs>' + json.dumps(data) + "</script></html>"


class InstagramFallbackTest(unittest.TestCase):
    def setUp(self):
        self.downloader = YoutubeDL({"quiet": True, "no_warnings": True}, auto_init=False)
        self.extractor = InstagramIE(self.downloader)
        self.extractor._can_impersonate = False
        self.extractor._lsd_token = "synthetic-test-token"
        self.extractor._get_cookies = Mock(return_value={})
        self.extractor._download_json = Mock(return_value={"status": "ok"})
        self.extractor._download_webpage = Mock(side_effect=AssertionError("Unexpected network request"))
        self.product = {
            "pk": "1234567890", "media_type": 2, "has_audio": True,
            "video_duration": 2, "user": {"username": "latch_fixture"},
            "video_versions": [{"type": 101, "url": CDN_URL, "width": 320, "height": 180}],
        }
        self.extractor._download_webpage_handle = Mock(
            return_value=(page(self.product), SimpleNamespace(url=URL)))

    def tearDown(self):
        self.downloader.close()

    def test_public_webpage_provides_real_video_without_impersonation(self):
        result = self.extractor._real_extract(URL)
        self.assertEqual(CDN_URL, result["formats"][0]["url"])
        self.assertEqual(180, result["formats"][0]["height"])
        self.assertEqual("https://www.instagram.com/", result["http_headers"]["Referer"])
        self.extractor._download_webpage_handle.assert_called_once()
        self.assertFalse(any("graphql" in str(call) for call in self.extractor._download_json.call_args_list))

    def test_login_redirect_reports_anonymous_rate_limit(self):
        self.extractor._download_webpage_handle.return_value = (
            "", SimpleNamespace(url="https://www.instagram.com/accounts/login/"))
        with self.assertRaisesRegex(ExtractorError, "rate-limit"):
            self.extractor._real_extract(URL)

    def test_gated_page_does_not_provide_formats(self):
        self.extractor._download_webpage_handle.return_value = (page(None), SimpleNamespace(url=URL))
        with self.assertRaisesRegex(ExtractorError, "empty media response"):
            self.extractor._real_extract(URL)

    def test_access_restriction_stops_before_webpage_fallback(self):
        self.extractor._download_json.return_value = {"title": "Restricted Video", "description": "Access required"}
        with self.assertRaises(ExtractorError):
            self.extractor._real_extract(URL)
        self.extractor._download_webpage_handle.assert_not_called()


if __name__ == "__main__":
    print("Testing bundled yt-dlp", __version__, flush=True)
    unittest.main(verbosity=2)
