"""Console-free launcher used by "Start with Windows"."""
import os
import sys

here = os.path.dirname(os.path.abspath(__file__))
os.chdir(here)
sys.path.insert(0, here)

from wizpor.app import main  # noqa: E402

main()
