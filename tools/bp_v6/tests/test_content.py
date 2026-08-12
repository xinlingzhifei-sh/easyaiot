from tools.bp_v6.content import (
    COMPANY,
    FORECAST,
    FUNDING,
    PILOT_TARGETS,
    PRICING,
    SLIDES,
)


def test_locked_company_facts():
    assert COMPANY["established"] == "2025-12-09"
    assert COMPANY["stage"] == "已有平台能力，商业化启动"
    assert COMPANY["revenue_since_establishment_wan"] == 0
    assert COMPANY["formal_customers"] == 0
    assert COMPANY["formal_contracts"] == 0
    assert COMPANY["formal_pilots"] == 0
    assert COMPANY["core_team_size"] == 1


def test_exactly_sixteen_slides():
    assert len(SLIDES) == 16
    assert [slide.number for slide in SLIDES] == list(range(1, 17))
    assert SLIDES[0].title == "让监管告警走向闭环办结"
    assert SLIDES[-1].title == "寻找首个付费试点与司法安防生态伙伴"


def test_forecast_is_explained_by_project_mix():
    assert FORECAST[2027]["target"] == 200
    assert FORECAST[2027]["calculated"] == 204.4
    assert FORECAST[2028]["target"] == 650
    assert FORECAST[2028]["calculated"] == 667.6
    assert FORECAST[2029]["target"] == 1500
    assert FORECAST[2029]["calculated"] == 1535.2


def test_pricing_funding_and_pilot_targets():
    assert PRICING == {
        "trial_8_route": 26.8,
        "standard_site": 75.4,
        "full_site": 156.2,
        "cluster_64_route": 188.8,
        "annual_governance": 15.8,
        "algorithm_rule_pack": 2.8,
    }
    assert sum(item["amount"] for item in FUNDING) == 500
    assert sum(item["ratio"] for item in FUNDING) == 100
    assert PILOT_TARGETS["routes"] == 8
    assert PILOT_TARGETS["days"] == 30
    assert PILOT_TARGETS["latency_p95_seconds"] == 10
    assert PILOT_TARGETS["evidence_completeness_percent"] == 95
    assert PILOT_TARGETS["closed_loop_percent"] == 90
    assert PILOT_TARGETS["repeat_false_positive_reduction_percent"] == 30


def test_planning_language_and_forbidden_claims():
    all_copy = "\n".join(
        slide.title + "\n" + "\n".join(slide.body) for slide in SLIDES
    )
    assert "试点验收目标，不是历史业绩" in all_copy
    for forbidden in (
        "100%自研",
        "完全自主知识产权",
        "国内领先",
        "已通过等保",
        "已服务多家单位",
        "已签约",
        "已落地",
    ):
        assert forbidden not in all_copy
