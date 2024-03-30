import struct

from fontTools.misc import etree
from fontTools.misc.testTools import getXML, parseXML
from fontTools.ttLib import TTFont
from fontTools.ttLib.tables.S_V_G_ import table_S_V_G_

import pytest


def dump(table, ttFont=None):
    print("\n".join(getXML(table.toXML, ttFont)))


def strip_xml_whitespace(xml_string):
    def strip_or_none(text):
        text = text.strip() if text else None
        return text if text else None

    tree = etree.fromstring(xml_string)
    for e in tree.iter("*"):
        e.text = strip_or_none(e.text)
        e.tail = strip_or_none(e.tail)
    return etree.tostring(tree, encoding="utf-8")


SVG_DOCS = [
    strip_xml_whitespace(svg)
    for svg in (
        b"""\
        <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" version="1.1">
          <defs>
            <rect x="100" y="-200" width="300" height="400" id="p1"/>
          </defs>
          <g id="glyph1">
            <use xlink:href="#p1" fill="#red"/>
          </g>
          <g id="glyph2">
            <use xlink:href="#p1" fill="#blue"/>
          </g>
          <g id="glyph4">
            <use xlink:href="#p1" fill="#green"/>
          </g>
        </svg>""",
        b"""\
        <svg xmlns="http://www.w3.org/2000/svg" version="1.1">
          <g id="glyph3">
            <path d="M0,0 L100,0 L50,100 Z"/>
          </g>
        </svg>""",
    )
]


OTSVG_DATA = b"".join(
    [
        # SVG table header
        b"\x00\x00"  # version (0)
        b"\x00\x00\x00\x0a"  # offset to SVGDocumentList (10)
        b"\x00\x00\x00\x00"  # reserved (0)
        #  SVGDocumentList
        b"\x00\x03"  # number of SVGDocumentRecords (3)
        # SVGDocumentRecord[0]
        b"\x00\x01"  # startGlyphID (1)
        b"\x00\x02"  # endGlyphID (2)
        b"\x00\x00\x00\x26"  # svgDocOffset (2 + 12*3 == 38 == 0x26)
        + struct.pack(">L", len(SVG_DOCS[0]))  # svgDocLength
        # SVGDocumentRecord[1]
        + b"\x00\x03"  # startGlyphID (3)
        b"\x00\x03"  # endGlyphID (3)
        + struct.pack(">L", 0x26 + len(SVG_DOCS[0]))  # svgDocOffset
        + struct.pack(">L", len(SVG_DOCS[1]))  # svgDocLength
        # SVGDocumentRecord[2]
        + b"\x00\x04"  # startGlyphID (4)
        b"\x00\x04"  # endGlyphID (4)
        b"\x00\x00\x00\x26"  # svgDocOffset (38); records 0 and 2 point to same SVG doc
        + struct.pack(">L", len(SVG_DOCS[0]))  # svgDocLength
    ]
    + SVG_DOCS
)

OTSVG_TTX = [
    '<svgDoc endGlyphID="2" startGlyphID="1">',
    f"  <![CDATA[{SVG_DOCS[0].decode()}]]>",
    "</svgDoc>",
    '<svgDoc endGlyphID="3" startGlyphID="3">',
    f"  <![CDATA[{SVG_DOCS[1].decode()}]]>",
    "</svgDoc>",
    '<svgDoc endGlyphID="4" startGlyphID="4">',
    f"  <![CDATA[{SVG_DOCS[0].decode()}]]>",
    "</svgDoc>",
]


@pytest.fixture
def font():
    font = TTFont()
    font.setGlyphOrder([".notdef"] + ["glyph%05d" % i for i in range(1, 30)])
    return font


def test_decompile_and_compile(font):
    table = table_S_V_G_()
    table.decompile(OTSVG_DATA, font)
    assert table.compile(font) == OTSVG_DATA


def test_decompile_and_dump_ttx(font):
    table = table_S_V_G_()
    table.decompile(OTSVG_DATA, font)

    dump(table, font)
    assert getXML(table.toXML, font) == OTSVG_TTX


def test_load_from_ttx_and_compile(font):
    table = table_S_V_G_()
    for name, attrs, content in parseXML(OTSVG_TTX):
        table.fromXML(name, attrs, content, font)
    assert table.compile(font) == OTSVG_DATA


def test_round_trip_ttx(font):
    table = table_S_V_G_()
    for name, attrs, content in parseXML(OTSVG_TTX):
        table.fromXML(name, attrs, content, font)
    compiled = table.compile(font)

    table = table_S_V_G_()
    table.decompile(compiled, font)
    assert getXML(table.toXML, font) == OTSVG_TTX
