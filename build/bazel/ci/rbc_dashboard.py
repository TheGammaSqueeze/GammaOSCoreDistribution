#!/usr/bin/env python3
"""Generates a dashboard for the current RBC product/board config conversion status."""
# pylint: disable=line-too-long

import argparse
import asyncio
import dataclasses
import datetime
import os
import re
import shutil
import socket
import subprocess
import sys
import time
from typing import List, Tuple
import xml.etree.ElementTree as ET

_PRODUCT_REGEX = re.compile(r'([a-zA-Z_][a-zA-Z0-9_]*)(?:-(user|userdebug|eng))?')


@dataclasses.dataclass(frozen=True)
class Product:
  """Represents a TARGET_PRODUCT and TARGET_BUILD_VARIANT."""
  product: str
  variant: str

  def __post_init__(self):
    if not _PRODUCT_REGEX.match(str(self)):
      raise ValueError(f'Invalid product name: {self}')

  def __str__(self):
    return self.product + '-' + self.variant


@dataclasses.dataclass(frozen=True)
class ProductResult:
  baseline_success: bool
  product_success: bool
  board_success: bool
  product_has_diffs: bool
  board_has_diffs: bool

  def success(self) -> bool:
    return not self.baseline_success or (
        self.product_success and self.board_success
        and not self.product_has_diffs and not self.board_has_diffs)


@dataclasses.dataclass(frozen=True)
class Directories:
  out: str
  out_baseline: str
  out_product: str
  out_board: str
  results: str


def get_top() -> str:
  path = '.'
  while not os.path.isfile(os.path.join(path, 'build/soong/soong_ui.bash')):
    if os.path.abspath(path) == '/':
      sys.exit('Could not find android source tree root.')
    path = os.path.join(path, '..')
  return os.path.abspath(path)


def get_build_var(variable, product: Product) -> str:
  """Returns the result of the shell command get_build_var."""
  env = {
      **os.environ,
      'TARGET_PRODUCT': product.product,
      'TARGET_BUILD_VARIANT': product.variant,
  }
  return subprocess.run([
      'build/soong/soong_ui.bash',
      '--dumpvar-mode',
      variable
  ], check=True, capture_output=True, env=env, text=True).stdout.strip()


async def run_jailed_command(args: List[str], out_dir: str, env=None) -> bool:
  """Runs a command, saves its output to out_dir/build.log, and returns if it succeeded."""
  with open(os.path.join(out_dir, 'build.log'), 'wb') as f:
    result = await asyncio.create_subprocess_exec(
        'prebuilts/build-tools/linux-x86/bin/nsjail',
        '-q',
        '--cwd',
        os.getcwd(),
        '-e',
        '-B',
        '/',
        '-B',
        f'{os.path.abspath(out_dir)}:{os.path.abspath("out")}',
        '--time_limit',
        '0',
        '--skip_setsid',
        '--keep_caps',
        '--disable_clone_newcgroup',
        '--disable_clone_newnet',
        '--rlimit_as',
        'soft',
        '--rlimit_core',
        'soft',
        '--rlimit_cpu',
        'soft',
        '--rlimit_fsize',
        'soft',
        '--rlimit_nofile',
        'soft',
        '--proc_rw',
        '--hostname',
        socket.gethostname(),
        '--',
        *args, stdout=f, stderr=subprocess.STDOUT, env=env)
    return await result.wait() == 0


async def run_build(flags: List[str], out_dir: str) -> bool:
  return await run_jailed_command([
      'build/soong/soong_ui.bash',
      '--make-mode',
      *flags,
      '--skip-ninja',
      'nothing'
  ], out_dir)


async def run_config(product: Product, rbc_product: bool, rbc_board: bool, out_dir: str) -> bool:
  """Runs config.mk and saves results to out/rbc_variable_dump.txt."""
  env = {
      'OUT_DIR': 'out',
      'TMPDIR': 'tmp',
      'BUILD_DATETIME_FILE': 'out/build_date.txt',
      'CALLED_FROM_SETUP': 'true',
      'TARGET_PRODUCT': product.product,
      'TARGET_BUILD_VARIANT': product.variant,
      'RBC_PRODUCT_CONFIG': 'true' if rbc_product else '',
      'RBC_BOARD_CONFIG': 'true' if rbc_board else '',
      'RBC_DUMP_CONFIG_FILE': 'out/rbc_variable_dump.txt',
  }
  return await run_jailed_command([
      'prebuilts/build-tools/linux-x86/bin/ckati',
      '-f',
      'build/make/core/config.mk'
  ], out_dir, env=env)


async def has_diffs(success: bool, file_pairs: List[Tuple[str]], results_folder: str) -> bool:
  """Returns true if the two out folders provided have differing ninja files."""
  if not success:
    return False
  results = []
  for pair in file_pairs:
    name = 'soong_build.ninja' if pair[0].endswith('soong/build.ninja') else os.path.basename(pair[0])
    with open(os.path.join(results_folder, name)+'.diff', 'wb') as f:
      results.append((await asyncio.create_subprocess_exec(
          'diff',
          pair[0],
          pair[1],
          stdout=f, stderr=subprocess.STDOUT)).wait())

  for return_code in await asyncio.gather(*results):
    if return_code != 0:
      return True
  return False


def generate_html_row(num: int, product: Product, results: ProductResult):
  def generate_status_cell(success: bool, diffs: bool) -> str:
    message = 'Success'
    if diffs:
      message = 'Results differed'
    if not success:
      message = 'Build failed'
    return f'<td style="background-color: {"lightgreen" if success and not diffs else "salmon"}">{message}</td>'

  return f'''
  <tr>
    <td>{num}</td>
    <td>{product if results.success() and results.baseline_success else f'<a href="{product}/">{product}</a>'}</td>
    {generate_status_cell(results.baseline_success, False)}
    {generate_status_cell(results.product_success, results.product_has_diffs)}
    {generate_status_cell(results.board_success, results.board_has_diffs)}
  </tr>
  '''


def get_branch() -> str:
  try:
    tree = ET.parse('.repo/manifests/default.xml')
    default_tag = tree.getroot().find('default')
    return default_tag.get('remote') + '/' + default_tag.get('revision')
  except Exception as e:  # pylint: disable=broad-except
    print(str(e), file=sys.stderr)
    return 'Unknown'


def cleanup_empty_files(path):
  if os.path.isfile(path):
    if os.path.getsize(path) == 0:
      os.remove(path)
  elif os.path.isdir(path):
    for subfile in os.listdir(path):
      cleanup_empty_files(os.path.join(path, subfile))
    if not os.listdir(path):
      os.rmdir(path)


async def test_one_product(product: Product, dirs: Directories) -> ProductResult:
  """Runs the builds and tests for differences for a single product."""
  baseline_success, product_success, board_success = await asyncio.gather(
      run_build([
          f'TARGET_PRODUCT={product.product}',
          f'TARGET_BUILD_VARIANT={product.variant}',
      ], dirs.out_baseline),
      run_build([
          f'TARGET_PRODUCT={product.product}',
          f'TARGET_BUILD_VARIANT={product.variant}',
          'RBC_PRODUCT_CONFIG=1',
      ], dirs.out_product),
      run_build([
          f'TARGET_PRODUCT={product.product}',
          f'TARGET_BUILD_VARIANT={product.variant}',
          'RBC_BOARD_CONFIG=1',
      ], dirs.out_board),
  )

  product_dashboard_folder = os.path.join(dirs.results, str(product))
  os.mkdir(product_dashboard_folder)
  os.mkdir(product_dashboard_folder+'/baseline')
  os.mkdir(product_dashboard_folder+'/product')
  os.mkdir(product_dashboard_folder+'/board')

  if not baseline_success:
    shutil.copy2(os.path.join(dirs.out_baseline, 'build.log'),
                 f'{product_dashboard_folder}/baseline/build.log')
  if not product_success:
    shutil.copy2(os.path.join(dirs.out_product, 'build.log'),
                 f'{product_dashboard_folder}/product/build.log')
  if not board_success:
    shutil.copy2(os.path.join(dirs.out_board, 'build.log'),
                 f'{product_dashboard_folder}/board/build.log')

  files = [f'build-{product.product}.ninja', f'build-{product.product}-package.ninja', 'soong/build.ninja']
  product_files = [(os.path.join(dirs.out_baseline, x), os.path.join(dirs.out_product, x)) for x in files]
  board_files = [(os.path.join(dirs.out_baseline, x), os.path.join(dirs.out_board, x)) for x in files]
  product_has_diffs, board_has_diffs = await asyncio.gather(
      has_diffs(baseline_success and product_success, product_files, product_dashboard_folder+'/product'),
      has_diffs(baseline_success and board_success, board_files, product_dashboard_folder+'/board'))

  # delete files that contain the product name in them to save space,
  # otherwise the ninja files end up filling up the whole harddrive
  for out_folder in [dirs.out_baseline, dirs.out_product, dirs.out_board]:
    for subfolder in ['', 'soong']:
      folder = os.path.join(out_folder, subfolder)
      for file in os.listdir(folder):
        if os.path.isfile(os.path.join(folder, file)) and product.product in file:
          os.remove(os.path.join(folder, file))

  cleanup_empty_files(product_dashboard_folder)

  return ProductResult(baseline_success, product_success, board_success, product_has_diffs, board_has_diffs)


async def test_one_product_quick(product: Product, dirs: Directories) -> ProductResult:
  """Runs the builds and tests for differences for a single product."""
  baseline_success, product_success, board_success = await asyncio.gather(
      run_config(
          product,
          False,
          False,
          dirs.out_baseline),
      run_config(
          product,
          True,
          False,
          dirs.out_product),
      run_config(
          product,
          False,
          True,
          dirs.out_board),
  )

  product_dashboard_folder = os.path.join(dirs.results, str(product))
  os.mkdir(product_dashboard_folder)
  os.mkdir(product_dashboard_folder+'/baseline')
  os.mkdir(product_dashboard_folder+'/product')
  os.mkdir(product_dashboard_folder+'/board')

  if not baseline_success:
    shutil.copy2(os.path.join(dirs.out_baseline, 'build.log'),
                 f'{product_dashboard_folder}/baseline/build.log')
  if not product_success:
    shutil.copy2(os.path.join(dirs.out_product, 'build.log'),
                 f'{product_dashboard_folder}/product/build.log')
  if not board_success:
    shutil.copy2(os.path.join(dirs.out_board, 'build.log'),
                 f'{product_dashboard_folder}/board/build.log')

  files = ['rbc_variable_dump.txt']
  product_files = [(os.path.join(dirs.out_baseline, x), os.path.join(dirs.out_product, x)) for x in files]
  board_files = [(os.path.join(dirs.out_baseline, x), os.path.join(dirs.out_board, x)) for x in files]
  product_has_diffs, board_has_diffs = await asyncio.gather(
      has_diffs(baseline_success and product_success, product_files, product_dashboard_folder+'/product'),
      has_diffs(baseline_success and board_success, board_files, product_dashboard_folder+'/board'))

  cleanup_empty_files(product_dashboard_folder)

  return ProductResult(baseline_success, product_success, board_success, product_has_diffs, board_has_diffs)


async def main():
  parser = argparse.ArgumentParser(
      description='Generates a dashboard of the starlark product configuration conversion.')
  parser.add_argument('products', nargs='*',
                      help='list of products to test. If not given, all '
                      + 'products will be tested. '
                      + 'Example: aosp_arm64-userdebug')
  parser.add_argument('--quick', action='store_true',
                      help='Run a quick test. This will only run config.mk and '
                      + 'diff the make variables at the end of it, instead of '
                      + 'diffing the full ninja files.')
  parser.add_argument('--exclude', nargs='+', default=[],
                      help='Exclude these producs from the build. Useful if not '
                      + 'supplying a list of products manually.')
  parser.add_argument('--results-directory',
                      help='Directory to store results in. Defaults to $(OUT_DIR)/rbc_dashboard. '
                      + 'Warning: will be cleared!')
  args = parser.parse_args()

  if args.results_directory:
    args.results_directory = os.path.abspath(args.results_directory)

  os.chdir(get_top())

  def str_to_product(p: str) -> Product:
    match = _PRODUCT_REGEX.fullmatch(p)
    if not match:
      sys.exit(f'Invalid product name: {p}. Example: aosp_arm64-userdebug')
    return Product(match.group(1), match.group(2) if match.group(2) else 'userdebug')

  products = [str_to_product(p) for p in args.products]

  if not products:
    products = list(map(lambda x: Product(x, 'userdebug'), get_build_var(
        'all_named_products', Product('aosp_arm64', 'userdebug')).split()))

  excluded = [str_to_product(p) for p in args.exclude]
  products = [p for p in products if p not in excluded]

  for i, product in enumerate(products):
    for j, product2 in enumerate(products):
      if i != j and product.product == product2.product:
        sys.exit(f'Product {product.product} cannot be repeated.')

  out_dir = get_build_var('OUT_DIR', Product('aosp_arm64', 'userdebug'))

  dirs = Directories(
      out=out_dir,
      out_baseline=os.path.join(out_dir, 'rbc_out_baseline'),
      out_product=os.path.join(out_dir, 'rbc_out_product'),
      out_board=os.path.join(out_dir, 'rbc_out_board'),
      results=args.results_directory if args.results_directory else os.path.join(out_dir, 'rbc_dashboard'))

  for folder in [dirs.out_baseline, dirs.out_product, dirs.out_board, dirs.results]:
    # delete and recreate the out directories. You can't reuse them for
    # a particular product, because after we delete some product-specific
    # files inside the out dir to save space, the build will fail if you
    # try to build the same product again.
    shutil.rmtree(folder, ignore_errors=True)
    os.makedirs(folder)

  # When running in quick mode, we still need to build
  # mk2rbc/rbcrun/AndroidProducts.mk.list, so run a get_build_var command to do
  # that in each folder.
  if args.quick:
    commands = []
    for folder in [dirs.out_baseline, dirs.out_product, dirs.out_board]:
      commands.append(run_jailed_command([
          'build/soong/soong_ui.bash',
          '--dumpvar-mode',
          'TARGET_PRODUCT'
      ], folder))
    for success in await asyncio.gather(*commands):
      if not success:
        sys.exit('Failed to setup output directories')

  with open(os.path.join(dirs.results, 'index.html'), 'w') as f:
    f.write(f'''
      <body>
        <h2>RBC Product/Board conversion status</h2>
        Generated on {datetime.date.today()} for branch {get_branch()}
        <table>
          <tr>
            <th>#</th>
            <th>product</th>
            <th>baseline</th>
            <th>RBC product config</th>
            <th>RBC board config</th>
          </tr>\n''')
    f.flush()

    all_results = []
    start_time = time.time()
    print(f'{"Current product":31.31} | {"Time Elapsed":>16} | {"Per each":>8} | {"ETA":>16} | Status')
    print('-' * 91)
    for i, product in enumerate(products):
      if i > 0:
        elapsed_time = time.time() - start_time
        time_per_product = elapsed_time / i
        eta = time_per_product * (len(products) - i)
        elapsed_time_str = str(datetime.timedelta(seconds=int(elapsed_time)))
        time_per_product_str = str(datetime.timedelta(seconds=int(time_per_product)))
        eta_str = str(datetime.timedelta(seconds=int(eta)))
        print(f'{f"{i+1}/{len(products)} {product}":31.31} | {elapsed_time_str:>16} | {time_per_product_str:>8} | {eta_str:>16} | ', end='', flush=True)
      else:
        print(f'{f"{i+1}/{len(products)} {product}":31.31} | {"":>16} | {"":>8} | {"":>16} | ', end='', flush=True)

      if not args.quick:
        result = await test_one_product(product, dirs)
      else:
        result = await test_one_product_quick(product, dirs)

      all_results.append(result)

      if result.success():
        print('Success')
      else:
        print('Failure')

      f.write(generate_html_row(i+1, product, result))
      f.flush()

    baseline_successes = len([x for x in all_results if x.baseline_success])
    product_successes = len([x for x in all_results if x.product_success and not x.product_has_diffs])
    board_successes = len([x for x in all_results if x.board_success and not x.board_has_diffs])
    f.write(f'''
          <tr>
            <td></td>
            <td># Successful</td>
            <td>{baseline_successes}</td>
            <td>{product_successes}</td>
            <td>{board_successes}</td>
          </tr>
          <tr>
            <td></td>
            <td># Failed</td>
            <td>N/A</td>
            <td>{baseline_successes - product_successes}</td>
            <td>{baseline_successes - board_successes}</td>
          </tr>
        </table>
        Finished running successfully.
      </body>\n''')

  print('Success!')
  print('file://'+os.path.abspath(os.path.join(dirs.results, 'index.html')))

  for result in all_results:
    if result.baseline_success and not result.success():
      print('There were one or more failing products. See the html report for details.')
      sys.exit(1)

if __name__ == '__main__':
  asyncio.run(main())
