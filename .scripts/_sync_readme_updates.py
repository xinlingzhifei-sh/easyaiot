#!/usr/bin/env python3
"""Sync README_zh.md updates to other language READMEs."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path("/projects/new/easyaiot")

NEW_CELL_CHEN = (
    '<td align="center" valign="top" width="11.11%">'
    '<a href="https://gitee.com/chen_jialin123" target="_blank">'
    '<img src="./.image/sponsor/陈家林.png" width="80px;" alt="陈家林"/>'
    "<br /><sub><b>陈家林</b></sub></a></td>"
)
NEW_CELL_NULL = (
    '<td align="center" valign="top" width="11.11%">'
    '<a href="javascript:void(0)" target="_blank">'
    '<img src="./.image/sponsor/NULL.png" width="80px;" alt="NULL"/>'
    "<br /><sub><b>NULL</b></sub></a></td>"
)


def td_name(td: str) -> str:
    m = re.search(r"<b>([^<]+)</b>", td) or re.search(r"<benen>([^<]+)</benen>", td)
    return m.group(1) if m else "?"


def rebuild_sponsor_table(text: str) -> tuple[str, dict]:
    """Match README_zh acknowledgement table order."""
    info: dict = {}
    if "sponsor/陈家林.png" in text:
        info["status"] = "already_updated"
        return text, info

    marker = 'src="./.image/sponsor/陈勇至.jpg"'
    idx = text.find(marker)
    if idx < 0:
        info["status"] = "error_no_chenyongzhi"
        return text, info

    table_start = text.rfind("<table>", 0, idx)
    table_end = text.find("</table>", idx)
    table = text[table_start : table_end + len("</table>")]
    tds = re.findall(r"<td\b[\s\S]*?</td>", table)
    info["original_count"] = len(tds)

    # Map by name (first occurrence wins for identity)
    by_name = {td_name(td): td.strip() for td in tds}
    names = [td_name(td) for td in tds]

    # Chinese-matching transform: prepend 2; move Achieve_Xu & NicholasLD before 常康
    filtered = [n for n in names if n not in ("Achieve_Xu", "NicholasLD")]
    assert filtered[-1] == "常康", filtered[-1]
    new_names = (
        ["陈家林", "NULL"]
        + filtered[:-1]
        + ["Achieve_Xu", "NicholasLD", "常康"]
    )
    new_tds = []
    for n in new_names:
        if n == "陈家林":
            new_tds.append(NEW_CELL_CHEN)
        elif n == "NULL" and n not in by_name:
            # use NEW_CELL_NULL for the inserted sponsor NULL (image in sponsor/)
            # but there might already be a NULL elsewhere? acknowledgement uses sponsor/NULL.png
            new_tds.append(NEW_CELL_NULL)
        else:
            new_tds.append(by_name[n])

    # Fix: when original has no name "NULL" in ack table, we use NEW_CELL_NULL.
    # When iterating 陈家林/NULL, first NULL is our insert. Good.
    # But by_name may not have 陈家林.

    rows = []
    row_counts = []
    for i in range(0, len(new_tds), 9):
        chunk = new_tds[i : i + 9]
        row_counts.append(len(chunk))
        inner = "\n".join("      " + td for td in chunk)
        rows.append(f"    <tr>\n{inner}\n    </tr>")

    new_table = "<table>\n  <tbody>\n" + "\n".join(rows) + "\n  </tbody>\n</table>"
    text = text[:table_start] + new_table + text[table_end + len("</table>") :]
    info["status"] = "ok"
    info["new_count"] = len(new_tds)
    info["row_counts"] = row_counts
    info["first9"] = new_names[:9]
    info["last3"] = new_names[-3:]
    return text, info


# ---------- Content replacements per language ----------

CONTENT = {
    "README.md": {
        "intro_old": (
            "On the capability side: GB28181 / ONVIF multi-protocol camera access, "
            "real-time and snapshot algorithm tasks, YOLO object detection and SAM zero-shot auto-annotation, "
            "face/plate recognition, orchestrable business post-processing, federated compute cluster scheduling, "
            "and <strong>Infinite Federated Edge Cluster mode</strong> (~512MB memory, Ceph with zero local disk at the edge, "
            "one-line command turns ordinary boards intelligent, sprawl compute and aggregate to the cloud), "
            "and MQTT / TCP / HTTP IoT device lifecycle management."
        ),
        "intro_new": (
            "On the capability side: GB28181 / ONVIF multi-protocol camera access, "
            "<strong>DJI Dock and drone aerial-view access</strong>, "
            "real-time and snapshot algorithm tasks, YOLO object detection and SAM zero-shot auto-annotation, "
            "face/plate recognition, orchestrable business post-processing, federated compute cluster scheduling, "
            "and <strong>Infinite Federated Edge Cluster mode</strong> "
            "(ordinary development boards ready to go, on-site intelligence decides locally, "
            "alerts and evidence aggregate to the cloud automatically, compute scales wherever the business goes), "
            "and MQTT / TCP / HTTP IoT device lifecycle management."
        ),
        "banner_old": """  <p style="font-size: 14px; line-height: 1.8; color: #333; margin: 0;">
    About <strong>512MB</strong> memory, <strong>Ceph with zero local disk at the edge</strong> (alerts and business objects write to shared Ceph—no local business disk); sprawl compute deployment with <strong>one command</strong> that turns ordinary development boards intelligent, while alerts and events aggregate to the cloud.
    Complements the three full-stack profiles above—full-stack owns cloud-edge control and orchestration; EDGE nodes own lightweight on-site inference and infinite horizontal scale-out: “one control plane, edge anywhere.”
  </p>""",
        "banner_new": """  <p style="font-size: 14px; line-height: 1.8; color: #333; margin: 0;">
    Bring intelligence from the data center to the field: ordinary development boards and edge boxes can sense and judge on site, while alerts, evidence, and situational awareness aggregate to the center automatically—no heavy infrastructure stacked at every site.
    Scale by adding nodes where business expands; the center orchestrates, the edge stands watch on its own—truly “compute follows the business, intelligence grows with the scene.”
  </p>""",
        "dji_item": (
            '  <li><strong>DJI FlightHub Aerial-View Access</strong>: Bring DJI Dock and drone high-altitude footage into the platform’s unified video system—'
            "ground monitoring and aerial patrol share the same screen, alerts, and judgment loop. Operators can review dock and aircraft live views "
            "just like fixed cameras, quickly covering wide-area patrol, emergency reconnaissance, and perimeter gap-filling that fixed points struggle to reach—"
            "shortening the chain from detection to lock-on to coordinated response, and upgrading smart security from planar coverage to air–ground collaborative sensing</li>\n"
        ),
        "camera_marker": "  <li><strong>Multi-Protocol Camera Access Support</strong>:",
        "intercom_marker": "  <li><strong>Real-Time Intercom & PTZ Remote Control</strong>:",
        "edge_ai_old_start": '  <li><strong>Infinite Federated Edge Cluster (EDGE)</strong>:',
        "edge_ai_new": (
            '  <li><strong>Infinite Federated Edge Cluster</strong>: Built for wide-area sites, weak networks, and staged expansion—'
            "put intelligence where the business runs. Ordinary development boards and edge compute nodes become always-ready watch units. "
            "The center pushes tasks and policies; the field senses and judges nearby; alerts and evidence flow back and aggregate automatically—"
            "without stacking heavy servers and complex ops at every site. Add nodes as you grow and coverage scales linearly: "
            "“one more site, one more area; one more stream, one more layer of assurance”—compute grows with the scene, intelligence unfolds with the business</li>"
        ),
        "edge_support_old": """<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>Infinite federated edge cluster: join with ~512MB memory</li>
  <li>Ceph: zero local disk footprint—business objects never land on local disks</li>
  <li>One command turns RK3588 and similar boards intelligent</li>
  <li>Sprawl compute by site; alerts and events aggregate to the cloud</li>
</ul>""",
        "edge_support_new": """<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>Ordinary development boards can stand intelligent watch on site</li>
  <li>Travel light in the field—no heavy storage stacked at every site</li>
  <li>Intelligence out of the box, shorter edge go-live cycles</li>
  <li>Compute scales with each site; alerts and evidence aggregate to the cloud automatically</li>
</ul>""",
        "edge_module_old_start": '<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>EDGE Module</strong></td>',
        "edge_module_new": """<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>EDGE Module</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">
  <ul style="margin: 5px 0; padding-left: 20px;">
    <li><strong>Infinite Federated Edge Cluster Mode</strong>: The eighth core module—extend intelligence from the center to the field. Ordinary development boards and edge nodes can join the watch network anytime; compute follows the business; alerts and evidence aggregate to the cloud automatically</li>
    <li><strong>Lightweight On-Site Watch</strong>: Focus on nearby sensing and reporting judgment results—without carrying heavy control UIs or local business systems—lowering edge deployment barriers and long-term ops burden</li>
    <li><strong>Out-of-the-Box Join & Unified Management</strong>: Field nodes join quickly; the center orchestrates tasks and policies uniformly, cutting manual configuration and per-site rebuild costs</li>
    <li><strong>Seamless Business Expansion</strong>: The center sees the big picture and sets the rules; the edge watches the scene and responds fast. Node count scales with coverage, supporting lateral roll-out of realtime analysis, patrol, and snapshot scenarios</li>
    <li><strong>Lean Deployment</strong>: The edge focuses on “getting work done,” not “piling hardware”—making wide-area rollout easier to land and easier to replicate</li>
  </ul>
</td>""",
        "contributor_chen": """<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>陈家林</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">To advance yFeiEye in IoT device interoperability and air–ground video fusion, delivered a closed loop for device commands and status data so the platform can truly “send down, see through, and stay in control”; also contributed DJI FlightHub dock and drone video access, bringing aerial patrol views into the unified video and alert system—significantly expanding the platform’s value in wide-area patrol, emergency reconnaissance, and air–ground collaborative sensing.</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>NULL</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">To advance yFeiEye in industrial field device access, delivered Modbus upstream data collection so meters, sensors, controllers, and other industrial devices can be unified for aggregation, monitoring, and linkage—completing the critical piece of “seeing the scene and hearing the devices,” and providing a solid foundation for industrial data acquisition, production-line intelligent control, and security linkage scenarios.</td>
</tr>
""",
        "thanks_old": (
            "yFeiEye-Edge end-to-end integration linking camera access with AI, "
            "and campus developer community organization and youth collaborative ecosystem building."
        ),
        "thanks_new": (
            "yFeiEye-Edge end-to-end integration linking camera access with AI, "
            "campus developer community organization and youth collaborative ecosystem building, "
            "IoT device uplink/downlink closed loop with DJI FlightHub aerial-view access, "
            "and industrial Modbus device upstream collection."
        ),
    },
}


def replace_edge_ai_item(text: str, start_marker: str, new_item: str) -> str:
    """Replace li that starts with start_marker through its closing </li>."""
    idx = text.find(start_marker)
    if idx < 0:
        raise ValueError(f"edge ai marker not found: {start_marker[:60]}")
    end = text.find("</li>", idx)
    if end < 0:
        raise ValueError("edge ai </li> not found")
    return text[:idx] + new_item + text[end + len("</li>") :]


def replace_edge_module(text: str, start_marker: str, new_block: str) -> str:
    """Replace EDGE module row's two <td>...</td> cells (module is second td with ul)."""
    idx = text.find(start_marker)
    if idx < 0:
        raise ValueError("EDGE module marker not found")
    # start_marker is first td; replace from first td through end of second td
    # Find closing of second </td> after the ul
    # Structure: <td>EDGE Module</td>\n<td>...ul...</td>
    second_td = text.find("<td", idx + 10)
    end = text.find("</td>", text.find("</ul>", second_td)) + len("</td>")
    return text[:idx] + new_block + text[end:]


def insert_dji_after_camera(text: str, camera_marker: str, intercom_marker: str, dji_item: str) -> str:
    if "DJI FlightHub Aerial-View" in text or "大疆司空空中视角" in text or "DJI Dock and drone" in dji_item and "DJI FlightHub" in text:
        # check language-specific
        pass
    cam = text.find(camera_marker)
    if cam < 0:
        raise ValueError("camera marker not found")
    inter = text.find(intercom_marker)
    if inter < 0:
        raise ValueError("intercom marker not found")
    # insert before intercom; camera li ends before intercom
    if dji_item.strip()[:40] in text:
        return text  # already
    return text[:inter] + dji_item + text[inter:]


def insert_contributors_after_li(text: str, block: str) -> str:
    if "陈家林</nobr>" in text and "Modbus" in text[text.find("陈家林</nobr>") : text.find("陈家林</nobr>") + 800]:
        return text  # rough already
    # Find Li row closing </tr> then insert
    # Look for <nobr>Li</nobr> in major contributors table
    m = re.search(
        r'(<td[^>]*><nobr>Li</nobr></td>\s*<td[^>]*>[\s\S]*?</td>\s*</tr>)',
        text,
    )
    if not m:
        raise ValueError("Li contributor row not found")
    insert_at = m.end()
    # Avoid double insert
    after = text[insert_at : insert_at + 200]
    if "陈家林" in after:
        return text
    return text[:insert_at] + "\n" + block + text[insert_at:]


def apply_common(text: str, cfg: dict, lang_checks: dict) -> tuple[str, list[str]]:
    done = []

    if cfg["intro_old"] not in text:
        if "DJI Dock" in text or "大疆机场" in text or lang_checks.get("intro_done"):
            done.append("intro:already")
        else:
            raise ValueError("intro_old not found")
    else:
        text = text.replace(cfg["intro_old"], cfg["intro_new"], 1)
        done.append("intro")

    if cfg["banner_old"] not in text:
        if "Bring intelligence from the data center" in text or lang_checks.get("banner_done"):
            done.append("banner:already")
        else:
            # try flexible
            raise ValueError("banner_old not found")
    else:
        text = text.replace(cfg["banner_old"], cfg["banner_new"], 1)
        done.append("banner")

    # DJI item
    dji_key = lang_checks.get("dji_detect", "DJI FlightHub Aerial-View Access")
    if dji_key in text:
        done.append("dji:already")
    else:
        text = insert_dji_after_camera(text, cfg["camera_marker"], cfg["intercom_marker"], cfg["dji_item"])
        done.append("dji")

    # EDGE AI list item
    if cfg["edge_ai_new"][:80] in text.replace("\n", "") or (
        "Infinite Federated Edge Cluster</strong>: Built for wide-area" in text
        or lang_checks.get("edge_ai_done")
    ):
        # may need language-specific already check
        if re.search(r"512MB|512MB|512 Mo|512 МБ|512MB", text[text.find(cfg["edge_ai_old_start"]) : text.find(cfg["edge_ai_old_start"]) + 500] if cfg["edge_ai_old_start"] in text else ""):
            text = replace_edge_ai_item(text, cfg["edge_ai_old_start"], cfg["edge_ai_new"])
            done.append("edge_ai")
        else:
            done.append("edge_ai:skip_or_already")
    elif cfg["edge_ai_old_start"] in text:
        text = replace_edge_ai_item(text, cfg["edge_ai_old_start"], cfg["edge_ai_new"])
        done.append("edge_ai")
    else:
        raise ValueError("edge_ai marker missing")

    if cfg["edge_support_old"] not in text:
        if "Ordinary development boards can stand" in text or lang_checks.get("edge_support_done"):
            done.append("edge_support:already")
        else:
            raise ValueError("edge_support_old not found")
    else:
        text = text.replace(cfg["edge_support_old"], cfg["edge_support_new"], 1)
        done.append("edge_support")

    if "Lean Deployment" in text or lang_checks.get("edge_module_done"):
        done.append("edge_module:already")
    else:
        text = replace_edge_module(text, cfg["edge_module_old_start"], cfg["edge_module_new"])
        done.append("edge_module")

    if "陈家林</nobr>" in text and "Modbus" in text:
        # check if contributor block exists
        done.append("contrib:check")
    text = insert_contributors_after_li(text, cfg["contributor_chen"])
    if "contrib:check" not in done:
        done.append("contrib")
    else:
        # insert_contributors may no-op
        done.append("contrib:done")

    if cfg["thanks_old"] not in text:
        if "industrial Modbus" in text or "Modbus" in text[text.find("Special Thanks") if "Special Thanks" in text else 0 :]:
            done.append("thanks:already_or_partial")
        else:
            raise ValueError("thanks_old not found")
    else:
        text = text.replace(cfg["thanks_old"], cfg["thanks_new"], 1)
        done.append("thanks")

    text, sinfo = rebuild_sponsor_table(text)
    done.append(f"sponsor:{sinfo}")
    return text, done


if __name__ == "__main__":
    # Only run README.md for now as test
    path = ROOT / "README.md"
    text = path.read_text(encoding="utf-8")
    cfg = CONTENT["README.md"]
    text2, done = apply_common(text, cfg, {})
    path.write_text(text2, encoding="utf-8")
    print("README.md", done)
