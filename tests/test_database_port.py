import json,os,subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).parents[1]
SOURCE=(ROOT/'stack.config/bookstack/init/50-configure-permissions.sh').read_text()
PREFIX=SOURCE.split('ENV_FILE=',1)[0].replace('#!/usr/bin/with-contenv bash','#!/bin/bash')
class Port(unittest.TestCase):
 def probe(self,port):
  with tempfile.TemporaryDirectory() as temp:
   binary=Path(temp)/'mariadb';binary.write_text('#!/usr/bin/python3\nimport json,sys\nprint(json.dumps(sys.argv[1:]))\n');binary.chmod(0o755)
   env=dict(os.environ,PATH=temp+':'+os.environ['PATH']);env.pop('DB_PORT',None)
   if port is not None:env['DB_PORT']=port
   result=subprocess.run(['bash','-c',PREFIX+'\nmariadb -h "host.containers.internal" -u "fixture" -p"synthetic & \\" password" --protocol=TCP "fixture_db" -e "SELECT 1"'],env=env,capture_output=True,text=True)
   self.assertEqual(0,result.returncode,result.stderr);return json.loads(result.stdout)
 def test_default_port_matches_existing_direct_database(self):self.assertEqual('--port=3306',self.probe(None)[0])
 def test_mapped_port_is_used_by_bootstrap_client(self):
  args=self.probe('56010');self.assertEqual('--port=56010',args[0]);self.assertIn('host.containers.internal',args);self.assertEqual('SELECT 1',args[-1])
 def test_port_is_one_literal_argument_not_shell_code(self):
  args=self.probe('56010; echo forbidden');self.assertEqual('--port=56010; echo forbidden',args[0])
 def test_all_bootstrap_sql_calls_use_the_port_aware_wrapper(self):
  self.assertEqual(1,SOURCE.count('command mariadb'))
  self.assertGreaterEqual(SOURCE.count('mariadb -h "$DB_HOST"'),8)
  self.assertIn('grep "^DB_PORT=" "$ENV_FILE"',SOURCE)
if __name__=='__main__':unittest.main()
