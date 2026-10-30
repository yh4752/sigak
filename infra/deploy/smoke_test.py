"""같은 origin에서 실제 데이터와 공개 경로 제한을 검증한다."""

import json
import sys
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen


BASE_URL = sys.argv.pop(1).rstrip('/') if len(sys.argv) > 1 else 'http://127.0.0.1:8088'


def fetch(path, method='GET', origin=None):
    headers = {'Origin': origin or BASE_URL} if method == 'POST' else {}
    request = Request(BASE_URL + path, method=method, headers=headers)
    try:
        response = urlopen(request, timeout=30)
    except HTTPError as error:
        response = error
    with response:
        return response.status, response.headers.get('Content-Type', ''), response.read()


class DeploymentSmokeTest(unittest.TestCase):
    def test_spa_and_deep_routes(self):
        for path in ('/', '/research', '/articles/1'):
            with self.subTest(path=path):
                status, content_type, body = fetch(path)
                self.assertEqual(status, 200)
                self.assertIn('text/html', content_type)
                self.assertIn(b'id="root"', body)

    def test_articles_search_and_detail_use_real_api(self):
        status, content_type, body = fetch('/api/articles')
        self.assertEqual(status, 200)
        self.assertIn('application/json', content_type)
        articles = json.loads(body)
        self.assertGreater(len(articles), 0)
        article_id = articles[0]['id']
        status, _, body = fetch(f'/api/articles/{article_id}')
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(body)['id'], article_id)
        status, _, body = fetch('/api/articles?query=graph')
        self.assertEqual(status, 200)
        self.assertIsInstance(json.loads(body), list)
        status, _, body = fetch(f'/api/articles/{article_id}/graph-context')
        self.assertEqual(status, 200)
        self.assertIsInstance(json.loads(body), dict)

    def test_research_uses_real_api(self):
        status, content_type, body = fetch('/api/research/overview')
        self.assertEqual(status, 200)
        self.assertIn('application/json', content_type)
        data = json.loads(body)
        self.assertTrue(data['retrieval']['available'])
        self.assertTrue(data['expandedCatalog']['available'])

    def test_internal_and_unknown_apis_are_not_public(self):
        for path in ('/api', '/api/internal/search-metrics/articles',
                     '/api/internal/collections/automation',
                     '/api/internal/summaries/budget',
                     '/api/%69nternal/search-metrics/articles',
                     '/api/articles/1/not-public', '/api/unknown'):
            with self.subTest(path=path):
                self.assertEqual(fetch(path)[0], 404)

    def test_summary_read_and_seed_request_are_public_without_paid_generation(self):
        status, _, body = fetch('/api/articles/1/summary')
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(body)['status'], 'READY')
        status, _, body = fetch('/api/articles/1/summary', method='POST')
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(body)['status'], 'READY')
        self.assertEqual(fetch('/api/articles/1/summary/extra', method='POST')[0], 405)
        self.assertEqual(fetch('/api/articles/1/summary', method='PUT')[0], 405)
        self.assertEqual(fetch('/api/articles/1/summary', method='POST', origin='https://untrusted.invalid')[0], 403)

    def test_write_method_is_not_public(self):
        self.assertEqual(fetch('/api/articles', method='POST')[0], 405)

    def test_hidden_files_are_not_spa_routes(self):
        for path in ('/.env', '/.git/config'):
            with self.subTest(path=path):
                self.assertEqual(fetch(path)[0], 404)


if __name__ == '__main__':
    unittest.main()
